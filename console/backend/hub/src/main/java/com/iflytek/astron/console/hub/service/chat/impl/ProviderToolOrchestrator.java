package com.iflytek.astron.console.hub.service.chat.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.dto.llm.SparkChatRequest;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared tool orchestration for bot debug and formal chat.
 *
 * Current provider capability matrix for enabled tools: spark uses native Spark web search;
 * OpenAI-compatible providers use model-driven function tool calling via web_search and
 * current_time; google and anthropic use their native web search tools.
 */
final class ProviderToolOrchestrator {

    static final String TOOL_WEB_SEARCH = "web_search";
    static final String TOOL_IFLY_SEARCH_LEGACY = "ifly_search";
    static final String TOOL_CURRENT_TIME = "current_time";
    static final String OPENAI_SEARCH_TOOL_NAME = "web_search";
    static final String OPENAI_CURRENT_TIME_TOOL_NAME = "current_time";
    static final String PROVIDER_SPARK = "spark";
    static final String PROVIDER_GOOGLE = "google";
    static final String PROVIDER_ANTHROPIC = "anthropic";

    private ProviderToolOrchestrator() {}

    static ToolExecutionPlan resolve(String provider, String openedTool) {
        Set<String> configuredTools = parseEnabledTools(openedTool);
        String normalizedProvider = normalizeProvider(provider);

        return switch (normalizedProvider) {
            case PROVIDER_SPARK -> new ToolExecutionPlan(
                    normalizedProvider,
                    configuredTools,
                    hasSearchTool(configuredTools) ? WebSearchMode.SPARK_NATIVE : WebSearchMode.DISABLED);
            case PROVIDER_GOOGLE -> new ToolExecutionPlan(
                    normalizedProvider,
                    configuredTools,
                    hasSearchTool(configuredTools) ? WebSearchMode.GOOGLE_NATIVE : WebSearchMode.DISABLED);
            case PROVIDER_ANTHROPIC -> new ToolExecutionPlan(
                    normalizedProvider,
                    configuredTools,
                    hasSearchTool(configuredTools) ? WebSearchMode.ANTHROPIC_NATIVE : WebSearchMode.DISABLED);
            default -> new ToolExecutionPlan(
                    normalizedProvider,
                    withDefaultBuiltInTools(configuredTools),
                    WebSearchMode.OPENAI_FUNCTION);
        };
    }

    static ToolExecutionPlan resolvePromptProvider(String provider, String openedTool) {
        return resolve(provider, openedTool);
    }

    static void applyToSparkRequest(SparkChatRequest request, ToolExecutionPlan plan) {
        request.setEnableWebSearch(plan.webSearchMode() == WebSearchMode.SPARK_NATIVE);
    }

    static void applyToPromptRequest(JSONObject request, ToolExecutionPlan plan) {
        switch (plan.webSearchMode()) {
            case DISABLED -> {
                return;
            }
            case GOOGLE_NATIVE -> request.put("tools", buildGoogleTools());
            case ANTHROPIC_NATIVE -> {
                request.put("tools", buildAnthropicTools());
                request.put("anthropicBeta", "web-search-2025-03-05");
            }
            case OPENAI_FUNCTION -> {
                boolean webSearchEnabled = plan.enabledTools().contains(TOOL_WEB_SEARCH)
                        || plan.enabledTools().contains(TOOL_IFLY_SEARCH_LEGACY);
                boolean currentTimeEnabled = plan.enabledTools().contains(TOOL_CURRENT_TIME);
                if (webSearchEnabled) {
                    request.put("managedWebSearch", true);
                }
                request.put("tools", buildOpenAiCompatibleTools(webSearchEnabled, currentTimeEnabled));
                request.put("tool_choice", "auto");
            }
            case SPARK_NATIVE -> {
            }
            default -> {
            }
        }
    }

    static String normalizeProvider(String provider) {
        if (StringUtils.isBlank(provider)) {
            return "openai";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> parseEnabledTools(String openedTool) {
        if (StringUtils.isBlank(openedTool)) {
            return Set.of();
        }
        List<String> tools = Arrays.stream(openedTool.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toList();
        return new LinkedHashSet<>(tools);
    }

    private static Set<String> withDefaultBuiltInTools(Set<String> openedTools) {
        LinkedHashSet<String> enabledTools = new LinkedHashSet<>(openedTools);
        enabledTools.remove(TOOL_IFLY_SEARCH_LEGACY);
        enabledTools.add(TOOL_WEB_SEARCH);
        enabledTools.add(TOOL_CURRENT_TIME);
        return enabledTools;
    }

    private static boolean hasSearchTool(Set<String> enabledTools) {
        return enabledTools.contains(TOOL_WEB_SEARCH) || enabledTools.contains(TOOL_IFLY_SEARCH_LEGACY);
    }

    private static JSONArray buildGoogleTools() {
        JSONArray tools = new JSONArray();
        tools.add(new JSONObject().fluentPut("google_search", new JSONObject()));
        return tools;
    }

    private static JSONArray buildAnthropicTools() {
        JSONArray tools = new JSONArray();
        tools.add(new JSONObject()
                .fluentPut("type", "web_search_20250305")
                .fluentPut("name", "web_search")
                .fluentPut("max_uses", 5));
        return tools;
    }

    private static JSONArray buildOpenAiCompatibleTools(boolean includeWebSearch, boolean includeCurrentTime) {
        JSONArray tools = new JSONArray();
        if (includeWebSearch) {
            tools.add(buildOpenAiFunctionTool(
                    OPENAI_SEARCH_TOOL_NAME,
                    "Search the live web for up-to-date external information. Use this for recent events, latest facts, prices, policies, schedules, releases, rankings, or status.",
                    new JSONObject()
                            .fluentPut("query", new JSONObject()
                                    .fluentPut("type", "string")
                                    .fluentPut("description", "A precise web search query based on the user's request.")),
                    List.of("query")));
        }
        if (includeCurrentTime) {
            tools.add(buildOpenAiFunctionTool(
                    OPENAI_CURRENT_TIME_TOOL_NAME,
                    "Get the current date, time, weekday, timezone, and ISO timestamp. Use this for questions about today, now, the current date, or the current weekday.",
                    new JSONObject()
                            .fluentPut("timezone", new JSONObject()
                                    .fluentPut("type", "string")
                                    .fluentPut("description", "IANA timezone name. Defaults to Asia/Shanghai.")),
                    List.of()));
        }
        return tools;
    }

    private static JSONObject buildOpenAiFunctionTool(String name, String description, JSONObject properties, List<String> requiredFields) {
        JSONObject function = new JSONObject();
        function.put("name", name);
        function.put("description", description);

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        JSONArray required = new JSONArray();
        required.addAll(requiredFields);
        parameters.put("required", required);
        parameters.put("additionalProperties", false);

        function.put("parameters", parameters);
        return new JSONObject()
                .fluentPut("type", "function")
                .fluentPut("function", function);
    }

    record ToolExecutionPlan(String provider, Set<String> enabledTools, WebSearchMode webSearchMode) {}

    enum WebSearchMode {
        DISABLED,
        SPARK_NATIVE,
        GOOGLE_NATIVE,
        ANTHROPIC_NATIVE,
        OPENAI_FUNCTION
    }
}
