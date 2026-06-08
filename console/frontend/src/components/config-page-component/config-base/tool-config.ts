export type ToolConfig = Record<string, boolean>;

const WEB_SEARCH_TOOL = 'web_search';
const LEGACY_WEB_SEARCH_TOOL = 'ifly_search';

const NEW_WORKBENCH_TOOL_OVERRIDES: ToolConfig = {
  [WEB_SEARCH_TOOL]: true,
  [LEGACY_WEB_SEARCH_TOOL]: false,
  text_to_image: false,
  codeinterpreter: false,
};

export const getEffectiveToolConfig = (
  toolConfig: ToolConfig | undefined,
  isNewWorkbench: boolean
): ToolConfig => {
  const baseConfig = normalizeToolConfig(toolConfig);
  if (!isNewWorkbench) {
    return baseConfig;
  }

  return {
    ...baseConfig,
    ...NEW_WORKBENCH_TOOL_OVERRIDES,
  };
};

export const serializeOpenedTool = (
  toolConfig: ToolConfig | undefined
): string => {
  const normalized = normalizeToolConfig(toolConfig);
  return Object.keys(normalized)
    .filter(key => normalized[key])
    .join(',');
};

const normalizeToolConfig = (toolConfig: ToolConfig | undefined): ToolConfig => {
  const normalized = { ...(toolConfig ?? {}) };
  if (normalized[LEGACY_WEB_SEARCH_TOOL]) {
    normalized[WEB_SEARCH_TOOL] = true;
    delete normalized[LEGACY_WEB_SEARCH_TOOL];
  }
  return normalized;
};

export const hasWebSearchTool = (openedTool?: string): boolean => {
  if (!openedTool) {
    return false;
  }
  return openedTool
    .split(',')
    .map(tool => tool.trim())
    .some(tool => tool === WEB_SEARCH_TOOL || tool === LEGACY_WEB_SEARCH_TOOL);
};
