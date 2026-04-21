import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  App,
  Button,
  Form,
  Input,
  Modal,
  Segmented,
  Tree,
  Typography,
} from 'antd';
import type { DataNode, TreeProps } from 'antd/es/tree';
import {
  CodeOutlined,
  DeleteOutlined,
  EditOutlined,
  FileMarkdownOutlined,
  FileOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  PlusOutlined,
  SaveOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import MonacoEditor from '@/components/monaco-editor';
import MarkdownRender from '@/components/markdown-render';
import {
  createSkillFile,
  createSkillFolder,
  deleteSkillEntry,
  getSkillFileContent,
  listSkillTree,
  moveSkillEntry,
  renameSkillEntry,
  updateSkillFileContent,
  uploadSkillFiles,
} from '@/services/skill';
import { SkillFileContent, SkillTreeNode } from '@/types/skill';
import styles from './index.module.scss';

const FILE_ACCEPT = '.md,.txt,.yaml,.yml,.json,.py,.js,.ts,.csv,.xml';

type DialogMode = 'folder' | 'file' | 'rename' | null;

function SkillPage(): React.ReactElement {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const uploadRef = useRef<HTMLInputElement | null>(null);
  const editorRef = useRef<{
    scrollToTop: () => void;
    scrollToBottom: () => void;
  } | null>(null);
  const [treeData, setTreeData] = useState<SkillTreeNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [currentFile, setCurrentFile] = useState<SkillFileContent | null>(null);
  const [editorValue, setEditorValue] = useState('');
  const [mode, setMode] = useState<'edit' | 'preview'>('edit');
  const [dialogMode, setDialogMode] = useState<DialogMode>(null);
  const [submitting, setSubmitting] = useState(false);
  const [dirty, setDirty] = useState(false);
  const keywordRef = useRef('');
  const selectedIdRef = useRef<number | null>(null);
  const submittingRef = useRef(false);

  useEffect(() => {
    keywordRef.current = keyword;
  }, [keyword]);

  useEffect(() => {
    selectedIdRef.current = selectedId;
  }, [selectedId]);

  useEffect(() => {
    submittingRef.current = submitting;
  }, [submitting]);

  const nodeMap = useMemo(() => {
    const map = new Map<number, SkillTreeNode>();
    const walk = (nodes: SkillTreeNode[]): void => {
      nodes.forEach(node => {
        map.set(node.id, node);
        if (node.children?.length) {
          walk(node.children);
        }
      });
    };
    walk(treeData);
    return map;
  }, [treeData]);

  const selectedNode = selectedId ? nodeMap.get(selectedId) || null : null;

  const loadTree = useCallback(
    async (
      nextKeyword?: string,
      nextSelectedId?: number | null
    ): Promise<void> => {
      const resolvedKeyword = nextKeyword ?? keywordRef.current;
      const resolvedSelectedId =
        typeof nextSelectedId === 'undefined'
          ? selectedIdRef.current
          : nextSelectedId;
      setLoading(true);
      try {
        const data = await listSkillTree(resolvedKeyword || undefined);
        const nextTreeData = data || [];
        setTreeData(nextTreeData);
        if (
          resolvedSelectedId &&
          !new Map(flattenNodes(nextTreeData).map(node => [node.id, node])).has(
            resolvedSelectedId
          )
        ) {
          setSelectedId(null);
          setCurrentFile(null);
          setEditorValue('');
          setDirty(false);
        }
      } finally {
        setLoading(false);
      }
    },
    []
  );

  const refreshTree = useCallback(
    async (
      nextKeyword?: string,
      nextSelectedId?: number | null
    ): Promise<void> => {
      await loadTree(nextKeyword, nextSelectedId);
    },
    [loadTree]
  );

  useEffect(() => {
    void loadTree('');
  }, [loadTree]);

  useEffect(() => {
    const handleSearch = (event: Event): void => {
      const detail = (event as CustomEvent<{ value: string; type: string }>)
        .detail;
      if (detail?.type !== 'skill') {
        return;
      }
      setKeyword(detail.value || '');
      void loadTree(detail.value || '');
    };
    const handleCreate = (): void => {
      form.resetFields();
      setDialogMode('folder');
    };
    window.addEventListener('headerSearch', handleSearch);
    window.addEventListener('headerCreateSkill', handleCreate);
    return (): void => {
      window.removeEventListener('headerSearch', handleSearch);
      window.removeEventListener('headerCreateSkill', handleCreate);
    };
  }, [form, loadTree]);

  const toTreeData = (nodes: SkillTreeNode[]): DataNode[] =>
    nodes.map(node => ({
      key: node.id,
      title: (
        <div className={styles.treeNode}>
          {node.entryType === 'folder' ? (
            <FolderOutlined />
          ) : node.skillEntry ? (
            <FileMarkdownOutlined />
          ) : (
            <FileOutlined />
          )}
          <div className={styles.treeLabel}>
            <span className={styles.treeName}>{node.name}</span>
            {node.skillEntry ? (
              <span className={styles.treeBadge}>SKILL</span>
            ) : null}
          </div>
        </div>
      ),
      children: toTreeData(node.children || []),
      isLeaf: node.entryType === 'file',
    }));

  const ensureDiscardChanges = async (): Promise<boolean> => {
    if (!dirty) {
      return true;
    }
    return await new Promise(resolve => {
      Modal.confirm({
        title: '放弃未保存的修改？',
        content: '当前文件还有未保存内容，继续切换会丢失本次修改。',
        okText: '放弃',
        cancelText: '继续编辑',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  };

  const openFile = async (id: number): Promise<void> => {
    const detail = await getSkillFileContent(id);
    setCurrentFile(detail);
    setEditorValue(detail.content || '');
    setDirty(false);
    setMode(detail.fileExt === 'md' ? 'preview' : 'edit');
    setTimeout(() => editorRef.current?.scrollToTop?.(), 0);
  };

  const handleSelect = async (keys: React.Key[]): Promise<void> => {
    const targetId = Number(keys?.[0]);
    if (!targetId || !(await ensureDiscardChanges())) {
      return;
    }
    setSelectedId(targetId);
    const node = nodeMap.get(targetId);
    if (node?.entryType === 'file') {
      await openFile(targetId);
    } else {
      setCurrentFile(null);
      setEditorValue('');
      setDirty(false);
    }
  };

  const handleSave = async (): Promise<void> => {
    if (
      submittingRef.current ||
      !selectedNode ||
      selectedNode.entryType !== 'file' ||
      !selectedId
    ) {
      return;
    }
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const saved = await updateSkillFileContent({
        id: selectedId,
        content: editorValue,
      });
      setCurrentFile(saved);
      setEditorValue(saved.content || '');
      setDirty(false);
      message.success('Skill 文件已保存');
      await loadTree(keywordRef.current, selectedId);
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  const handleDialogSubmit = async (): Promise<void> => {
    if (submittingRef.current) {
      return;
    }
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const values = await form.validateFields();
      const parentId =
        selectedNode?.entryType === 'folder'
          ? selectedNode.id
          : selectedNode?.parentId || 0;

      if (dialogMode === 'folder') {
        await createSkillFolder({
          parentId,
          name: values.name,
        });
        setDialogMode(null);
        form.resetFields();
        message.success('文件夹已创建');
        await refreshTree(keywordRef.current);
      } else if (dialogMode === 'file') {
        const created = await createSkillFile({
          parentId,
          name: values.name,
          content: values.content || '',
        });
        setDialogMode(null);
        form.resetFields();
        message.success('文件已创建');
        setSelectedId(created.id);
        setCurrentFile(created);
        setEditorValue(created.content || '');
        setMode(created.fileExt === 'md' ? 'preview' : 'edit');
        setDirty(false);
        await refreshTree(keywordRef.current, created.id);
      } else if (dialogMode === 'rename' && selectedNode) {
        await renameSkillEntry({
          id: selectedNode.id,
          name: values.name,
        });
        setDialogMode(null);
        form.resetFields();
        message.success('名称已更新');
        await refreshTree(keywordRef.current, selectedNode.id);
        if (selectedNode.entryType === 'file') {
          await openFile(selectedNode.id);
        }
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  const handleDelete = async (): Promise<void> => {
    if (!selectedNode || submittingRef.current) {
      return;
    }
    Modal.confirm({
      title: `删除${selectedNode.entryType === 'folder' ? '文件夹' : '文件'}？`,
      content:
        selectedNode.entryType === 'folder'
          ? '删除后会同时移除其下所有 Skill 文件。'
          : '删除后不可恢复。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        if (submittingRef.current) {
          return;
        }
        submittingRef.current = true;
        setSubmitting(true);
        try {
          await deleteSkillEntry(selectedNode.id);
          message.success('已删除');
          setSelectedId(null);
          setCurrentFile(null);
          setEditorValue('');
          setDirty(false);
          await refreshTree(keywordRef.current, null);
        } finally {
          submittingRef.current = false;
          setSubmitting(false);
        }
      },
    });
  };

  const handleRename = (): void => {
    if (!selectedNode) {
      return;
    }
    form.setFieldsValue({ name: selectedNode.name });
    setDialogMode('rename');
  };

  const handleUpload = async (files: FileList | null): Promise<void> => {
    if (submittingRef.current || !files?.length) {
      return;
    }
    const parentId =
      selectedNode?.entryType === 'folder'
        ? selectedNode.id
        : selectedNode?.parentId || 0;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const uploaded = await uploadSkillFiles(parentId, Array.from(files));
      message.success(`已上传 ${uploaded.length} 个文件`);
      const firstFile = uploaded[0];
      await refreshTree(
        keywordRef.current,
        firstFile?.id || selectedIdRef.current
      );
      if (firstFile) {
        setSelectedId(firstFile.id);
        setCurrentFile(firstFile);
        setEditorValue(firstFile.content || '');
        setMode(firstFile.fileExt === 'md' ? 'preview' : 'edit');
        setDirty(false);
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
      if (uploadRef.current) {
        uploadRef.current.value = '';
      }
    }
  };

  const onDrop: TreeProps['onDrop'] = async info => {
    if (submittingRef.current) {
      return;
    }
    const dragId = Number(info.dragNode.key);
    const dropId = Number(info.node.key);
    const targetNode = nodeMap.get(dropId);
    if (!targetNode) {
      return;
    }
    submittingRef.current = true;
    let targetParentId = 0;
    let sortOrder = 0;
    if (!info.dropToGap && targetNode.entryType === 'folder') {
      targetParentId = targetNode.id;
      sortOrder = (targetNode.children?.length || 0) + 1;
    } else {
      targetParentId = targetNode.parentId || 0;
      const siblings = flattenNodes(treeData).filter(
        node => node.parentId === targetParentId
      );
      sortOrder = Math.max(
        siblings.findIndex(node => node.id === dropId) + 1,
        0
      );
    }
    setSubmitting(true);
    try {
      await moveSkillEntry({
        id: dragId,
        targetParentId,
        sortOrder,
      });
      await refreshTree(keywordRef.current, dragId);
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  const renderEmpty = (): React.ReactElement => (
    <div className={styles.empty}>
      <div className={styles.emptyCard}>
        <div className={styles.eyebrow}>
          <span className={styles.statusDot}></span>
          Skill Filesystem
        </div>
        <div className={styles.emptyTitle}>用文件组织你的 Agent 技能</div>
        <div className={styles.emptyDesc}>
          推荐以“技能目录 + `SKILL.md` + 辅助脚本/参考文件”的结构管理内容。 这样
          Agent 节点只注入 `name` 与 `description`，需要细节时再通过 skill
          工具按需读取完整 `SKILL.md`。
        </div>
        <div className={styles.folderActions}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.resetFields();
              setDialogMode('folder');
            }}
          >
            新建文件夹
          </Button>
          <Button
            icon={<CodeOutlined />}
            onClick={() => {
              form.setFieldsValue({ name: 'SKILL.md', content: '' });
              setDialogMode('file');
            }}
          >
            新建 SKILL.md
          </Button>
        </div>
      </div>
    </div>
  );

  const renderFolderView = (): React.ReactElement => (
    <div className={styles.empty}>
      <div className={styles.emptyCard}>
        <div className={styles.eyebrow}>
          <FolderOpenOutlined />
          当前目录
        </div>
        <div className={styles.emptyTitle}>{selectedNode?.name}</div>
        <div className={styles.emptyDesc}>
          在这个目录下创建 `SKILL.md`、说明文档、脚本和参考资源。目录中存在
          `SKILL.md` 时，它会出现在 Agent 节点的 skill 导入列表中。
        </div>
        <div className={styles.folderActions}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.resetFields();
              setDialogMode('folder');
            }}
          >
            新建文件夹
          </Button>
          <Button
            icon={<CodeOutlined />}
            onClick={() => {
              form.setFieldsValue({ name: 'SKILL.md', content: '' });
              setDialogMode('file');
            }}
          >
            新建文件
          </Button>
          <Button
            icon={<UploadOutlined />}
            onClick={() => uploadRef.current?.click()}
          >
            上传文件
          </Button>
          <Button icon={<EditOutlined />} onClick={handleRename}>
            重命名
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={handleDelete}>
            删除
          </Button>
        </div>
      </div>
    </div>
  );

  const renderFileView = (): React.ReactElement => (
    <>
      <div className={styles.editorHeader}>
        <div className={styles.editorMeta}>
          <div className={styles.editorTitleRow}>
            {selectedNode?.skillEntry ? (
              <FileMarkdownOutlined />
            ) : (
              <FileOutlined />
            )}
            <div className={styles.editorTitle}>{selectedNode?.name}</div>
            {selectedNode?.skillEntry ? (
              <span className={styles.treeBadge}>SKILL</span>
            ) : null}
            {dirty ? <span className={styles.monoTag}>UNSAVED</span> : null}
          </div>
          <div className={styles.editorSub}>
            {currentFile?.skillName ? (
              <span>技能名: {currentFile.skillName}</span>
            ) : null}
            {currentFile?.skillDescription ? (
              <span>{currentFile.skillDescription}</span>
            ) : null}
            <span className={styles.monoTag}>
              .{currentFile?.fileExt || 'txt'}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Segmented
            value={mode}
            onChange={value => setMode(value as 'edit' | 'preview')}
            options={[
              { label: '编辑', value: 'edit' },
              { label: '预览', value: 'preview' },
            ]}
          />
          <Button icon={<EditOutlined />} onClick={handleRename}>
            重命名
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={handleDelete}>
            删除
          </Button>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={submitting}
            onClick={() => void handleSave()}
          >
            保存
          </Button>
        </div>
      </div>
      <div className={styles.editorBody}>
        <div className={styles.editorCanvas}>
          {mode === 'edit' ? (
            <MonacoEditor
              ref={editorRef}
              language={resolveLanguage(currentFile?.fileExt)}
              value={editorValue}
              onChange={value => {
                setEditorValue(value || '');
                setDirty(true);
              }}
              options={{
                fontSize: 13,
                wordWrap: 'on',
              }}
            />
          ) : currentFile?.fileExt === 'md' ? (
            <div className={styles.preview}>
              <MarkdownRender content={editorValue} isSending={false} />
            </div>
          ) : (
            <pre className={styles.codePreview}>{editorValue}</pre>
          )}
        </div>
      </div>
    </>
  );

  return (
    <div className={styles.page}>
      <input
        ref={uploadRef}
        type="file"
        accept={FILE_ACCEPT}
        multiple
        hidden
        onChange={event => void handleUpload(event.target.files)}
      />
      <div className={styles.shell}>
        <div className={`${styles.panel} ${styles.sidebar}`}>
          <div className={styles.sidebarHeader}>
            <div className={styles.eyebrow}>
              <span className={styles.statusDot}></span>
              Resource / Skill
            </div>
            <div className={styles.title}>Skill 文件系统</div>
            <div className={styles.desc}>
              用目录组织 `SKILL.md`、脚本和参考文件，支持在线编辑与 Agent
              节点渐进式加载。
            </div>
            <div className={styles.toolbar}>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  form.resetFields();
                  setDialogMode('folder');
                }}
              >
                文件夹
              </Button>
              <Button
                icon={<CodeOutlined />}
                onClick={() => {
                  form.setFieldsValue({ name: 'SKILL.md', content: '' });
                  setDialogMode('file');
                }}
              >
                文件
              </Button>
              <Button
                icon={<UploadOutlined />}
                onClick={() => uploadRef.current?.click()}
              >
                上传
              </Button>
            </div>
            {loading ? (
              <Typography.Text type="secondary">加载中...</Typography.Text>
            ) : keyword ? (
              <Typography.Text type="secondary">
                当前筛选: {keyword}
              </Typography.Text>
            ) : null}
          </div>
          <div className={styles.treeWrap}>
            <Tree
              blockNode
              draggable
              showLine
              selectedKeys={selectedId ? [selectedId] : []}
              treeData={toTreeData(treeData)}
              onSelect={keys => void handleSelect(keys)}
              onDrop={info => void onDrop(info)}
            />
          </div>
        </div>

        <div className={`${styles.panel} ${styles.editorPanel}`}>
          {!selectedNode
            ? renderEmpty()
            : selectedNode.entryType === 'folder'
              ? renderFolderView()
              : renderFileView()}
        </div>
      </div>

      <Modal
        open={dialogMode !== null}
        title={
          dialogMode === 'folder'
            ? '新建文件夹'
            : dialogMode === 'file'
              ? '新建文件'
              : '重命名'
        }
        okText="确定"
        cancelText="取消"
        confirmLoading={submitting}
        onCancel={() => {
          setDialogMode(null);
          form.resetFields();
        }}
        onOk={handleDialogSubmit}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="名称"
            name="name"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input
              placeholder={
                dialogMode === 'folder' ? '例如：xiaohongshu' : '例如：SKILL.md'
              }
            />
          </Form.Item>
          {dialogMode === 'file' ? (
            <Form.Item label="初始内容" name="content">
              <Input.TextArea
                rows={8}
                placeholder="支持 Markdown / YAML / 代码文本"
              />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </div>
  );
}

function flattenNodes(nodes: SkillTreeNode[]): SkillTreeNode[] {
  return nodes.flatMap(node => [node, ...flattenNodes(node.children || [])]);
}

function resolveLanguage(fileExt?: string): string {
  switch (fileExt) {
    case 'md':
      return 'markdown';
    case 'json':
      return 'json';
    case 'py':
      return 'python';
    case 'js':
      return 'javascript';
    case 'ts':
      return 'typescript';
    case 'xml':
      return 'xml';
    case 'yaml':
    case 'yml':
      return 'yaml';
    default:
      return 'plaintext';
  }
}

export default SkillPage;
