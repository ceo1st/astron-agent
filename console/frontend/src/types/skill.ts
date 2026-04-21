export interface SkillTreeNode {
  id: number;
  parentId: number;
  name: string;
  entryType: 'folder' | 'file';
  sortOrder?: number;
  fileExt?: string;
  fileSize?: number;
  skillEntry?: boolean;
  skillName?: string;
  skillDescription?: string;
  updateTime?: string;
  children?: SkillTreeNode[];
}

export interface SkillFileContent {
  id: number;
  name: string;
  entryType: 'folder' | 'file';
  fileExt?: string;
  content: string;
  fileSize?: number;
  skillName?: string;
  skillDescription?: string;
  updateTime?: string;
}

export interface SkillImportItem {
  id: number;
  parentId: number;
  folderName?: string;
  fileName?: string;
  name: string;
  description: string;
  downloadUrl?: string;
  updateTime?: string;
}

export interface CreateSkillFolderParams {
  parentId?: number;
  name: string;
}

export interface CreateSkillFileParams {
  parentId?: number;
  name: string;
  content?: string;
}

export interface RenameSkillEntryParams {
  id: number;
  name: string;
}

export interface MoveSkillEntryParams {
  id: number;
  targetParentId?: number;
  sortOrder?: number;
}

export interface UpdateSkillFileContentParams {
  id: number;
  content: string;
}
