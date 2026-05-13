import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  message,
  Space,
  Switch,
} from 'antd';
import {
  getSkillSandboxConfig,
  saveSkillSandboxConfig,
  testSkillSandboxConfig,
} from '@/services/sandbox';
import type { SkillSandboxConfig } from '@/types/sandbox';
import type { RuleObject } from 'rc-field-form/es/interface';

const DEFAULT_CONFIG: SkillSandboxConfig = {
  provider: 'e2b',
  enabled: false,
  apiKey: '',
  apiKeyMasked: false,
  timeoutSeconds: 300,
  allowInternetAccess: false,
};

function SandboxPage(): React.ReactElement {
  const [form] = Form.useForm<SkillSandboxConfig>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [loadedConfig, setLoadedConfig] =
    useState<SkillSandboxConfig>(DEFAULT_CONFIG);

  const loadConfig = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getSkillSandboxConfig();
      const nextConfig = { ...DEFAULT_CONFIG, ...data, provider: 'e2b' };
      setLoadedConfig(nextConfig);
      form.setFieldsValue(nextConfig);
    } finally {
      setLoading(false);
    }
  }, [form]);

  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

  const normalizeSubmitConfig = useCallback(
    (values: SkillSandboxConfig): SkillSandboxConfig => {
      const apiKeyMasked =
        Boolean(loadedConfig.apiKeyMasked) &&
        values.apiKey === loadedConfig.apiKey;
      return {
        ...DEFAULT_CONFIG,
        ...values,
        provider: 'e2b',
        apiKeyMasked,
      };
    },
    [loadedConfig]
  );

  const handleSave = async (): Promise<void> => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const data = await saveSkillSandboxConfig(normalizeSubmitConfig(values));
      const nextConfig = { ...DEFAULT_CONFIG, ...data, provider: 'e2b' };
      setLoadedConfig(nextConfig);
      form.setFieldsValue(nextConfig);
      message.success('脚本沙箱配置已保存');
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async (): Promise<void> => {
    const values = await form.validateFields();
    setTesting(true);
    try {
      const data = await testSkillSandboxConfig(normalizeSubmitConfig(values));
      const nextConfig = { ...DEFAULT_CONFIG, ...data, provider: 'e2b' };
      setLoadedConfig(nextConfig);
      form.setFieldsValue(nextConfig);
      if (data.lastTestStatus === 'success') {
        message.success(data.lastTestMessage || '脚本沙箱配置可用');
      } else {
        message.warning(data.lastTestMessage || '脚本沙箱配置未通过测试');
      }
    } finally {
      setTesting(false);
    }
  };

  const lastTestText = useMemo(() => {
    if (!loadedConfig.lastTestStatus) {
      return '尚未测试';
    }
    return `${loadedConfig.lastTestStatus === 'success' ? '测试通过' : '测试失败'}${
      loadedConfig.lastTestTime ? ` · ${loadedConfig.lastTestTime}` : ''
    }`;
  }, [loadedConfig.lastTestStatus, loadedConfig.lastTestTime]);

  return (
    <div className="h-full overflow-auto bg-[#f7f8fc] px-8 py-6">
      <div className="mx-auto max-w-[980px]">
        <div className="mb-5">
          <h1 className="m-0 text-[20px] font-medium text-[#222529]">
            脚本沙箱
          </h1>
          <p className="mt-2 mb-0 text-[13px] leading-5 text-[#676773]">
            配置后，Agent 节点可通过 run_skill_xxx 工具在第三方沙箱中执行 Skill
            脚本。
          </p>
        </div>

        <Form
          form={form}
          layout="vertical"
          initialValues={DEFAULT_CONFIG}
          disabled={loading}
          className="rounded-lg border border-[#e2e8ff] bg-white"
        >
          <div className="flex items-center justify-between border-b border-[#edf0fb] px-6 py-4">
            <div>
              <div className="text-[16px] font-medium text-[#222529]">
                E2B Sandbox
              </div>
              <div className="mt-1 text-[12px] text-[#8a8f9d]">
                {lastTestText}
              </div>
            </div>
            <Form.Item name="enabled" valuePropName="checked" noStyle>
              <Switch checkedChildren="启用" unCheckedChildren="停用" />
            </Form.Item>
          </div>

          <div className="px-6 py-5">
            <Alert
              type="info"
              showIcon
              className="mb-5"
              message="未配置沙箱时，run_skill_xxx 会固定提示当前环境暂不支持直接执行 Skill 脚本。"
            />
            <Form.Item name="provider" hidden>
              <Input />
            </Form.Item>
            <Form.Item
              label="API Key"
              name="apiKey"
              rules={[
                ({ getFieldValue }): RuleObject => ({
                  validator(_, value): Promise<void> {
                    if (!getFieldValue('enabled') || value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(
                      new Error('启用脚本沙箱后需要填写 E2B API Key')
                    );
                  },
                }),
              ]}
            >
              <Input.Password
                placeholder="输入 E2B API Key"
                autoComplete="new-password"
                className="max-w-[520px]"
              />
            </Form.Item>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Form.Item
                label="执行超时"
                name="timeoutSeconds"
                rules={[{ required: true, message: '请设置执行超时时间' }]}
              >
                <InputNumber
                  min={30}
                  max={1800}
                  addonAfter="秒"
                  className="w-full"
                />
              </Form.Item>
              <Form.Item
                label="允许访问公网"
                name="allowInternetAccess"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>
            </div>

            {loadedConfig.lastTestMessage && (
              <div className="mb-5 rounded-md bg-[#f7f8fc] px-4 py-3 text-[13px] text-[#676773]">
                {loadedConfig.lastTestMessage}
              </div>
            )}

            <Space>
              <Button type="primary" loading={saving} onClick={handleSave}>
                保存配置
              </Button>
              <Button loading={testing} onClick={handleTest}>
                测试配置
              </Button>
            </Space>
          </div>
        </Form>
      </div>
    </div>
  );
}

export default SandboxPage;
