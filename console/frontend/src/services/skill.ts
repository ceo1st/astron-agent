import http from '@/utils/http';
import {
  CreateSkillFileParams,
  CreateSkillFolderParams,
  MoveSkillEntryParams,
  RenameSkillEntryParams,
  SkillFileContent,
  SkillImportItem,
  SkillTreeNode,
  UpdateSkillFileContentParams,
} from '@/types/skill';

export async function listSkillTree(
  keyword?: string
): Promise<SkillTreeNode[]> {
  return await http.get('/skill-file/tree', {
    params: { keyword },
  });
}

export async function getSkillFileContent(
  id: number
): Promise<SkillFileContent> {
  return await http.get('/skill-file/content', {
    params: { id },
  });
}

export async function createSkillFolder(
  params: CreateSkillFolderParams
): Promise<void> {
  await http.post('/skill-file/folder', params);
}

export async function createSkillFile(
  params: CreateSkillFileParams
): Promise<SkillFileContent> {
  return await http.post('/skill-file/file', params);
}

export async function uploadSkillFiles(
  parentId: number,
  files: File[]
): Promise<SkillFileContent[]> {
  const formData = new FormData();
  formData.append('parentId', String(parentId ?? 0));
  files.forEach(file => formData.append('files', file));
  return await http.post('/skill-file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export async function updateSkillFileContent(
  params: UpdateSkillFileContentParams
): Promise<SkillFileContent> {
  return await http.put('/skill-file/content', params);
}

export async function renameSkillEntry(
  params: RenameSkillEntryParams
): Promise<void> {
  await http.put('/skill-file/rename', params);
}

export async function moveSkillEntry(
  params: MoveSkillEntryParams
): Promise<void> {
  await http.put('/skill-file/move', params);
}

export async function deleteSkillEntry(id: number): Promise<void> {
  await http.delete('/skill-file', {
    params: { id },
  });
}

export async function listImportableSkills(
  keyword?: string
): Promise<SkillImportItem[]> {
  return await http.get('/skill-file/importable', {
    params: { keyword },
  });
}
