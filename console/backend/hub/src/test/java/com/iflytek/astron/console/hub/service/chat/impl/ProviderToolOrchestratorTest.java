package com.iflytek.astron.console.hub.service.chat.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.dto.llm.SparkChatRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderToolOrchestratorTest {

    @Test
    void testApplyToPromptRequest_OpenAiDefaultsWebSearchAndCurrentTimeTools() {
        ProviderToolOrchestrator.ToolExecutionPlan plan = ProviderToolOrchestrator.resolve("openai", "");
        JSONObject request = new JSONObject();

        ProviderToolOrchestrator.applyToPromptRequest(request, plan);

        assertTrue(request.getBooleanValue("managedWebSearch"));
        assertEquals("auto", request.getString("tool_choice"));
        JSONArray tools = request.getJSONArray("tools");
        assertNotNull(tools);
        assertEquals(2, tools.size());
        assertEquals("web_search", tools.getJSONObject(0)
                .getJSONObject("function")
                .getString("name"));
        assertEquals("current_time", tools.getJSONObject(1)
                .getJSONObject("function")
                .getString("name"));
    }

    @Test
    void testApplyToPromptRequest_OpenAiIgnoresSavedToolTogglesForDefaultBuiltIns() {
        ProviderToolOrchestrator.ToolExecutionPlan plan = ProviderToolOrchestrator.resolve("openai", "current_time");
        JSONObject request = new JSONObject();

        ProviderToolOrchestrator.applyToPromptRequest(request, plan);

        assertTrue(request.getBooleanValue("managedWebSearch"));
        assertEquals("auto", request.getString("tool_choice"));
        JSONArray tools = request.getJSONArray("tools");
        assertNotNull(tools);
        assertEquals(2, tools.size());
        assertEquals("web_search", tools.getJSONObject(0)
                .getJSONObject("function")
                .getString("name"));
        assertEquals("current_time", tools.getJSONObject(1)
                .getJSONObject("function")
                .getString("name"));
    }

    @Test
    void testApplyToPromptRequest_SparkDoesNotUseOpenAiCompatibleBuiltInTools() {
        ProviderToolOrchestrator.ToolExecutionPlan plan = ProviderToolOrchestrator.resolve("spark", "");
        JSONObject request = new JSONObject();

        ProviderToolOrchestrator.applyToPromptRequest(request, plan);

        assertEquals("spark", plan.provider());
        assertEquals(ProviderToolOrchestrator.WebSearchMode.DISABLED, plan.webSearchMode());
        assertFalse(request.containsKey("managedWebSearch"));
        assertFalse(request.containsKey("tool_choice"));
        assertFalse(request.containsKey("tools"));
    }

    @Test
    void testApplyToSparkRequest_ExplicitSearchUsesNativeSparkWebSearch() {
        ProviderToolOrchestrator.ToolExecutionPlan plan = ProviderToolOrchestrator.resolve("spark", "ifly_search");
        SparkChatRequest request = new SparkChatRequest();

        ProviderToolOrchestrator.applyToSparkRequest(request, plan);

        assertEquals("spark", plan.provider());
        assertEquals(ProviderToolOrchestrator.WebSearchMode.SPARK_NATIVE, plan.webSearchMode());
        assertTrue(request.getEnableWebSearch());
        assertEquals("deep", request.getSearchMode());
        assertTrue(request.getShowRefLabel());
    }

    @Test
    void testApplyToPromptRequest_GoogleWithoutExplicitSearchKeepsNativeToolsDisabled() {
        ProviderToolOrchestrator.ToolExecutionPlan plan = ProviderToolOrchestrator.resolve("google", "");
        JSONObject request = new JSONObject();

        ProviderToolOrchestrator.applyToPromptRequest(request, plan);

        assertEquals("google", plan.provider());
        assertEquals(ProviderToolOrchestrator.WebSearchMode.DISABLED, plan.webSearchMode());
        assertFalse(request.containsKey("tools"));
        assertFalse(request.containsKey("managedWebSearch"));
    }

    @Test
    void testApplyToPromptRequest_AnthropicWithoutExplicitSearchKeepsNativeToolsDisabled() {
        ProviderToolOrchestrator.ToolExecutionPlan plan = ProviderToolOrchestrator.resolve("anthropic", "");
        JSONObject request = new JSONObject();

        ProviderToolOrchestrator.applyToPromptRequest(request, plan);

        assertEquals("anthropic", plan.provider());
        assertEquals(ProviderToolOrchestrator.WebSearchMode.DISABLED, plan.webSearchMode());
        assertFalse(request.containsKey("tools"));
        assertFalse(request.containsKey("anthropicBeta"));
    }
}
