package com.iflytek.astron.console.toolkit.service.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.util.S3ClientUtil;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.toolkit.entity.dto.skill.SkillDirectoryUploadResultDto;
import com.iflytek.astron.console.toolkit.entity.dto.skill.SkillFileContentDto;
import com.iflytek.astron.console.toolkit.entity.dto.skill.SkillFileTreeNodeDto;
import com.iflytek.astron.console.toolkit.entity.dto.skill.SkillImportDto;
import com.iflytek.astron.console.toolkit.entity.table.skill.SkillFile;
import com.iflytek.astron.console.toolkit.entity.vo.skill.SkillFileCreateFolderReq;
import com.iflytek.astron.console.toolkit.entity.vo.skill.SkillFileCreateReq;
import com.iflytek.astron.console.toolkit.entity.vo.skill.SkillFileMoveReq;
import com.iflytek.astron.console.toolkit.entity.vo.skill.SkillFileRenameReq;
import com.iflytek.astron.console.toolkit.entity.vo.skill.SkillFileUpdateContentReq;
import com.iflytek.astron.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astron.console.toolkit.mapper.skill.SkillFileMapper;
import com.iflytek.astron.console.toolkit.util.S3Util;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SkillFileService extends ServiceImpl<SkillFileMapper, SkillFile> {

    private static final Set<String> ALLOWED_FILE_EXTS = Set.of(
            "md", "txt", "yaml", "yml", "json", "py", "js", "ts", "csv", "xml");
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final String ENTRY_TYPE_FOLDER = "folder";
    private static final String ENTRY_TYPE_FILE = "file";
    private static final String SKILL_FILE_NAME = "SKILL.md";
    private static final Pattern FRONTMATTER_PATTERN =
            Pattern.compile("(?s)^---\\s*(.*?)\\s*---\\s*(.*)$");

    @Resource
    private S3Util s3Util;

    @Resource
    private S3ClientUtil s3ClientUtil;

    public List<SkillFileTreeNodeDto> listTree(String keyword) {
        List<SkillFile> entries = listScopedEntries();
        if (StringUtils.isNotBlank(keyword)) {
            entries = filterTreeEntries(entries, keyword.trim());
        }
        return buildTree(entries);
    }

    public SkillFileContentDto getContent(Long id) {
        SkillFile file = getScopedEntry(id);
        if (!ENTRY_TYPE_FILE.equals(file.getEntryType())) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
        return toContentDto(file, readContent(file));
    }

    @Transactional
    public SkillFileTreeNodeDto createFolder(SkillFileCreateFolderReq req) {
        String name = normalizeFolderName(req.getName());
        Long parentId = normalizeParentId(req.getParentId());
        assertParentFolder(parentId);
        assertDuplicateName(parentId, name, null);

        SkillFile folder = new SkillFile();
        folder.setUid(currentUid());
        folder.setSpaceId(currentSpaceId());
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setEntryType(ENTRY_TYPE_FOLDER);
        folder.setSortOrder(nextSortOrder(parentId));
        folder.setDeleted(Boolean.FALSE);
        folder.setCreateTime(LocalDateTime.now());
        folder.setUpdateTime(LocalDateTime.now());
        save(folder);
        return toTreeNode(folder);
    }

    @Transactional
    public SkillFileContentDto createFile(SkillFileCreateReq req) {
        String name = normalizeFileName(req.getName());
        Long parentId = normalizeParentId(req.getParentId());
        String content = StringUtils.defaultString(req.getContent());
        assertParentFolder(parentId);
        assertDuplicateName(parentId, name, null);

        SkillFile file = new SkillFile();
        file.setUid(currentUid());
        file.setSpaceId(currentSpaceId());
        file.setParentId(parentId);
        file.setName(name);
        file.setEntryType(ENTRY_TYPE_FILE);
        file.setSortOrder(nextSortOrder(parentId));
        file.setDeleted(Boolean.FALSE);
        file.setCreateTime(LocalDateTime.now());
        file.setUpdateTime(LocalDateTime.now());
        file.setFileExt(fileExt(name));
        file.setContentType(resolveContentType(name));
        upsertFileContent(file, content);
        save(file);
        updateById(file);
        return toContentDto(file, content);
    }

    @Transactional
    public List<SkillFileContentDto> uploadFiles(Long parentId, MultipartFile[] files) {
        Long normalizedParentId = normalizeParentId(parentId);
        assertParentFolder(normalizedParentId);
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<SkillFileContentDto> result = new ArrayList<>();
        for (MultipartFile upload : files) {
            String originalName = StringUtils.defaultString(upload.getOriginalFilename()).trim();
            String normalizedName = normalizeFileName(originalName);
            validateUpload(upload, normalizedName);
            assertDuplicateName(normalizedParentId, normalizedName, null);

            String content = readMultipartContent(upload);
            SkillFile file = new SkillFile();
            file.setUid(currentUid());
            file.setSpaceId(currentSpaceId());
            file.setParentId(normalizedParentId);
            file.setName(normalizedName);
            file.setEntryType(ENTRY_TYPE_FILE);
            file.setSortOrder(nextSortOrder(normalizedParentId));
            file.setDeleted(Boolean.FALSE);
            file.setCreateTime(LocalDateTime.now());
            file.setUpdateTime(LocalDateTime.now());
            file.setFileExt(fileExt(normalizedName));
            file.setContentType(resolveContentType(normalizedName));
            upsertFileContent(file, content);
            save(file);
            updateById(file);
            result.add(toContentDto(file, content));
        }
        return result;
    }

    @Transactional
    public SkillDirectoryUploadResultDto uploadDirectory(List<String> paths, MultipartFile[] files) {
        if (files == null || files.length == 0 || paths == null || paths.size() != files.length) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
        List<SkillFileTreeNodeDto> uploadedNodes = new ArrayList<>();
        for (int index = 0; index < files.length; index++) {
            MultipartFile upload = files[index];
            List<String> segments = normalizeUploadPath(paths.get(index));
            Long currentParentId = 0L;
            for (int segmentIndex = 0; segmentIndex < segments.size() - 1; segmentIndex++) {
                String folderName = normalizeFolderName(segments.get(segmentIndex));
                SkillFile folder = resolveOrCreateFolder(currentParentId, folderName);
                if (uploadedNodes.stream().noneMatch(item -> Objects.equals(item.getId(), folder.getId()))) {
                    uploadedNodes.add(toTreeNode(folder));
                }
                currentParentId = folder.getId();
            }

            String fileName = normalizeFileName(segments.get(segments.size() - 1));
            validateUpload(upload, fileName);
            assertDuplicateName(currentParentId, fileName, null);

            String content = readMultipartContent(upload);
            SkillFile file = buildFileEntry(currentParentId, fileName, content);
            save(file);
            updateById(file);
            uploadedNodes.add(toTreeNode(file));
        }
        SkillDirectoryUploadResultDto result = new SkillDirectoryUploadResultDto();
        result.setUploadedNodes(uploadedNodes.stream()
                .sorted(Comparator
                        .comparing((SkillFileTreeNodeDto node) -> ENTRY_TYPE_FILE.equals(node.getEntryType()))
                        .thenComparing(node -> node.getSortOrder() == null ? 0 : node.getSortOrder())
                        .thenComparing(node -> StringUtils.defaultString(node.getName()), String.CASE_INSENSITIVE_ORDER))
                .toList());
        result.setTree(listTree(null));
        return result;
    }

    @Transactional
    public SkillFileContentDto updateContent(SkillFileUpdateContentReq req) {
        SkillFile file = getScopedEntry(req.getId());
        if (!ENTRY_TYPE_FILE.equals(file.getEntryType())) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
        String content = StringUtils.defaultString(req.getContent());
        upsertFileContent(file, content);
        file.setUpdateTime(LocalDateTime.now());
        updateById(file);
        return toContentDto(file, content);
    }

    @Transactional
    public SkillFileTreeNodeDto rename(SkillFileRenameReq req) {
        SkillFile entry = getScopedEntry(req.getId());
        String name = ENTRY_TYPE_FOLDER.equals(entry.getEntryType())
                ? normalizeFolderName(req.getName())
                : normalizeFileName(req.getName());
        assertDuplicateName(entry.getParentId(), name, entry.getId());
        entry.setName(name);
        if (ENTRY_TYPE_FILE.equals(entry.getEntryType())) {
            entry.setFileExt(fileExt(name));
            entry.setContentType(resolveContentType(name));
            if (isSkillFile(name)) {
                SkillMetadata metadata = extractSkillMetadata(readContent(entry), parentFolderName(entry));
                entry.setSkillName(metadata.getName());
                entry.setSkillDescription(metadata.getDescription());
            } else {
                entry.setSkillName(null);
                entry.setSkillDescription(null);
            }
        }
        entry.setUpdateTime(LocalDateTime.now());
        updateById(entry);
        return toTreeNode(entry);
    }

    @Transactional
    public SkillFileTreeNodeDto move(SkillFileMoveReq req) {
        SkillFile entry = getScopedEntry(req.getId());
        Long targetParentId = normalizeParentId(req.getTargetParentId());
        assertParentFolder(targetParentId);
        if (Objects.equals(entry.getId(), targetParentId)) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
        if (ENTRY_TYPE_FOLDER.equals(entry.getEntryType()) && isDescendant(targetParentId, entry.getId())) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
        assertDuplicateName(targetParentId, entry.getName(), entry.getId());
        entry.setParentId(targetParentId);
        entry.setSortOrder(req.getSortOrder() == null ? nextSortOrder(targetParentId) : Math.max(req.getSortOrder(), 0));
        entry.setUpdateTime(LocalDateTime.now());
        updateById(entry);
        return toTreeNode(entry);
    }

    @Transactional
    public void delete(Long id) {
        SkillFile entry = getScopedEntry(id);
        List<SkillFile> scoped = listScopedEntries();
        Map<Long, List<SkillFile>> childrenMap = scoped.stream()
                .collect(Collectors.groupingBy(SkillFile::getParentId));
        List<SkillFile> toDelete = new ArrayList<>();
        Deque<SkillFile> queue = new ArrayDeque<>();
        queue.add(entry);
        while (!queue.isEmpty()) {
            SkillFile current = queue.poll();
            toDelete.add(current);
            for (SkillFile child : childrenMap.getOrDefault(current.getId(), Collections.emptyList())) {
                queue.add(child);
            }
        }
        List<String> objectKeys = toDelete.stream()
                .map(SkillFile::getObjectKey)
                .filter(StringUtils::isNotBlank)
                .toList();
        LocalDateTime now = LocalDateTime.now();
        for (SkillFile item : toDelete) {
            item.setDeleted(Boolean.TRUE);
            item.setUpdateTime(now);
            updateById(item);
        }
        if (!objectKeys.isEmpty()) {
            s3Util.batchDeleteObject(objectKeys);
        }
    }

    public List<SkillImportDto> listImportableSkills(String keyword) {
        List<SkillFile> files = listScopedEntries().stream()
                .filter(item -> ENTRY_TYPE_FILE.equals(item.getEntryType()) && isSkillFile(item.getName()))
                .filter(item -> StringUtils.isBlank(keyword) || matchesSkillKeyword(item, keyword.trim()))
                .sorted(Comparator.comparing(SkillFile::getUpdateTime, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .toList();
        List<SkillImportDto> result = new ArrayList<>();
        for (SkillFile file : files) {
            SkillImportDto dto = new SkillImportDto();
            dto.setId(file.getId());
            dto.setParentId(file.getParentId());
            dto.setFileName(file.getName());
            dto.setFolderName(parentFolderName(file));
            dto.setName(resolveSkillName(file));
            dto.setDescription(resolveSkillDescription(file));
            dto.setUpdateTime(file.getUpdateTime());
            if (StringUtils.isNotBlank(file.getObjectKey())) {
                dto.setDownloadUrl(s3ClientUtil.generatePresignedGetUrl(file.getObjectKey()));
            }
            result.add(dto);
        }
        return result;
    }

    public List<SkillImportDto> getSkillImportsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> selected = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return listImportableSkills(null).stream()
                .filter(item -> selected.contains(item.getId()))
                .toList();
    }

    private List<SkillFile> listScopedEntries() {
        return list(scopeQuery().orderByAsc(SkillFile::getSortOrder).orderByAsc(SkillFile::getId));
    }

    private List<SkillFile> filterTreeEntries(List<SkillFile> entries, String keyword) {
        Map<Long, SkillFile> entryMap = entries.stream()
                .collect(Collectors.toMap(SkillFile::getId, item -> item));
        Set<Long> keepIds = new HashSet<>();
        String lowered = keyword.toLowerCase(Locale.ROOT);
        for (SkillFile entry : entries) {
            if (matchesTreeKeyword(entry, lowered)) {
                SkillFile current = entry;
                while (current != null && keepIds.add(current.getId())) {
                    current = entryMap.get(current.getParentId());
                }
            }
        }
        return entries.stream().filter(item -> keepIds.contains(item.getId())).toList();
    }

    private boolean matchesTreeKeyword(SkillFile entry, String loweredKeyword) {
        return StringUtils.containsIgnoreCase(entry.getName(), loweredKeyword)
                || StringUtils.containsIgnoreCase(entry.getSkillName(), loweredKeyword)
                || StringUtils.containsIgnoreCase(entry.getSkillDescription(), loweredKeyword);
    }

    private List<SkillFileTreeNodeDto> buildTree(List<SkillFile> entries) {
        Map<Long, SkillFileTreeNodeDto> dtoMap = new HashMap<>();
        for (SkillFile entry : entries) {
            dtoMap.put(entry.getId(), toTreeNode(entry));
        }
        List<SkillFileTreeNodeDto> roots = new ArrayList<>();
        for (SkillFile entry : entries) {
            SkillFileTreeNodeDto dto = dtoMap.get(entry.getId());
            SkillFileTreeNodeDto parent = dtoMap.get(entry.getParentId());
            if (parent == null) {
                roots.add(dto);
            } else {
                parent.getChildren().add(dto);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<SkillFileTreeNodeDto> nodes) {
        nodes.sort(Comparator
                .comparing((SkillFileTreeNodeDto node) -> ENTRY_TYPE_FILE.equals(node.getEntryType()))
                .thenComparing(node -> node.getSortOrder() == null ? 0 : node.getSortOrder())
                .thenComparing(node -> StringUtils.defaultString(node.getName()), String.CASE_INSENSITIVE_ORDER));
        for (SkillFileTreeNodeDto node : nodes) {
            sortTree(node.getChildren());
        }
    }

    private SkillFileTreeNodeDto toTreeNode(SkillFile entry) {
        SkillFileTreeNodeDto dto = new SkillFileTreeNodeDto();
        dto.setId(entry.getId());
        dto.setParentId(entry.getParentId());
        dto.setName(entry.getName());
        dto.setEntryType(entry.getEntryType());
        dto.setSortOrder(entry.getSortOrder());
        dto.setFileExt(entry.getFileExt());
        dto.setFileSize(entry.getFileSize());
        dto.setSkillEntry(isSkillFile(entry.getName()));
        dto.setSkillName(resolveSkillName(entry));
        dto.setSkillDescription(resolveSkillDescription(entry));
        dto.setUpdateTime(entry.getUpdateTime());
        return dto;
    }

    private SkillFileContentDto toContentDto(SkillFile file, String content) {
        SkillFileContentDto dto = new SkillFileContentDto();
        dto.setId(file.getId());
        dto.setParentId(file.getParentId());
        dto.setName(file.getName());
        dto.setEntryType(file.getEntryType());
        dto.setSortOrder(file.getSortOrder());
        dto.setFileExt(file.getFileExt());
        dto.setContent(content);
        dto.setFileSize(file.getFileSize());
        dto.setSkillEntry(isSkillFile(file.getName()));
        dto.setSkillName(resolveSkillName(file));
        dto.setSkillDescription(resolveSkillDescription(file));
        dto.setUpdateTime(file.getUpdateTime());
        return dto;
    }

    private LambdaQueryWrapper<SkillFile> scopeQuery() {
        LambdaQueryWrapper<SkillFile> wrapper = Wrappers.lambdaQuery(SkillFile.class)
                .eq(SkillFile::getDeleted, Boolean.FALSE);
        Long spaceId = currentSpaceId();
        if (spaceId != null) {
            wrapper.eq(SkillFile::getSpaceId, spaceId);
        } else {
            wrapper.isNull(SkillFile::getSpaceId).eq(SkillFile::getUid, currentUid());
        }
        return wrapper;
    }

    private SkillFile getScopedEntry(Long id) {
        if (id == null) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
        SkillFile entry = getOne(scopeQuery().eq(SkillFile::getId, id), false);
        if (entry == null) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
        return entry;
    }

    private void assertParentFolder(Long parentId) {
        if (parentId == 0L) {
            return;
        }
        SkillFile parent = getScopedEntry(parentId);
        if (!ENTRY_TYPE_FOLDER.equals(parent.getEntryType())) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
    }

    private void assertDuplicateName(Long parentId, String name, Long currentId) {
        SkillFile duplicate = getOne(scopeQuery()
                .eq(SkillFile::getParentId, parentId)
                .eq(SkillFile::getName, name)
                .ne(currentId != null, SkillFile::getId, currentId), false);
        if (duplicate != null) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
    }

    private int nextSortOrder(Long parentId) {
        return list(scopeQuery().eq(SkillFile::getParentId, parentId)).stream()
                .map(SkillFile::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private void validateUpload(MultipartFile upload, String normalizedName) {
        if (upload.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
        validateAllowedExtension(normalizedName);
    }

    private List<String> normalizeUploadPath(String path) {
        String normalized = StringUtils.trimToEmpty(path).replace("\\", "/");
        if (StringUtils.isBlank(normalized) || normalized.startsWith("/") || normalized.endsWith("/")) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
        List<String> segments = Arrays.stream(normalized.split("/"))
                .map(StringUtils::trimToEmpty)
                .toList();
        if (segments.isEmpty() || segments.stream().anyMatch(StringUtils::isBlank)) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
        return segments;
    }

    private SkillFile resolveOrCreateFolder(Long parentId, String folderName) {
        SkillFile existing = findEntryByParentAndName(parentId, folderName);
        if (existing != null) {
            if (!ENTRY_TYPE_FOLDER.equals(existing.getEntryType())) {
                throw new BusinessException(ResponseEnum.PARAM_ERROR);
            }
            return existing;
        }
        SkillFile folder = buildFolderEntry(parentId, folderName);
        save(folder);
        return folder;
    }

    private SkillFile findEntryByParentAndName(Long parentId, String name) {
        return getOne(scopeQuery()
                .eq(SkillFile::getParentId, parentId)
                .eq(SkillFile::getName, name), false);
    }

    private String normalizeFolderName(String name) {
        String normalized = StringUtils.trimToEmpty(name);
        if (StringUtils.isBlank(normalized)
                || ".".equals(normalized)
                || "..".equals(normalized)
                || normalized.contains("/")
                || normalized.contains("\\")) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
        return normalized;
    }

    private String normalizeFileName(String name) {
        String normalized = normalizeFolderName(name);
        validateAllowedExtension(normalized);
        return normalized;
    }

    private void validateAllowedExtension(String name) {
        String ext = fileExt(name);
        if (!ALLOWED_FILE_EXTS.contains(ext)) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR);
        }
    }

    private String fileExt(String name) {
        String ext = StringUtils.substringAfterLast(StringUtils.defaultString(name), ".");
        return StringUtils.lowerCase(ext);
    }

    private String resolveContentType(String name) {
        String ext = fileExt(name);
        return switch (ext) {
            case "md" -> "text/markdown";
            case "txt" -> "text/plain";
            case "yaml", "yml" -> "application/x-yaml";
            case "json" -> "application/json";
            case "py", "js", "ts", "csv", "xml" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private SkillFile buildFolderEntry(Long parentId, String name) {
        SkillFile folder = new SkillFile();
        folder.setUid(currentUid());
        folder.setSpaceId(currentSpaceId());
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setEntryType(ENTRY_TYPE_FOLDER);
        folder.setSortOrder(nextSortOrder(parentId));
        folder.setDeleted(Boolean.FALSE);
        folder.setCreateTime(LocalDateTime.now());
        folder.setUpdateTime(LocalDateTime.now());
        return folder;
    }

    private SkillFile buildFileEntry(Long parentId, String name, String content) {
        SkillFile file = new SkillFile();
        file.setUid(currentUid());
        file.setSpaceId(currentSpaceId());
        file.setParentId(parentId);
        file.setName(name);
        file.setEntryType(ENTRY_TYPE_FILE);
        file.setSortOrder(nextSortOrder(parentId));
        file.setDeleted(Boolean.FALSE);
        file.setCreateTime(LocalDateTime.now());
        file.setUpdateTime(LocalDateTime.now());
        file.setFileExt(fileExt(name));
        file.setContentType(resolveContentType(name));
        upsertFileContent(file, content);
        return file;
    }

    private void upsertFileContent(SkillFile file, String content) {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        if (StringUtils.isBlank(file.getObjectKey())) {
            file.setObjectKey(generateObjectKey(file.getName()));
        }
        s3Util.putObject(file.getObjectKey(), data, resolveContentType(file.getName()));
        file.setFileSize((long) data.length);
        file.setFileExt(fileExt(file.getName()));
        file.setContentType(resolveContentType(file.getName()));
        if (isSkillFile(file.getName())) {
            SkillMetadata metadata = extractSkillMetadata(content, parentFolderName(file));
            file.setSkillName(metadata.getName());
            file.setSkillDescription(metadata.getDescription());
        } else {
            file.setSkillName(null);
            file.setSkillDescription(null);
        }
    }

    private String generateObjectKey(String fileName) {
        String scope = currentSpaceId() != null ? "space-" + currentSpaceId() : "user-" + safeSegment(currentUid());
        return "skill-files/" + scope + "/" + UUID.randomUUID().toString().replace("-", "")
                + "/" + safeSegment(fileName);
    }

    private String safeSegment(String value) {
        return StringUtils.defaultString(value)
                .replaceAll("[^a-zA-Z0-9._-]", "-")
                .replaceAll("-{2,}", "-");
    }

    private String readContent(SkillFile file) {
        if (StringUtils.isBlank(file.getObjectKey())) {
            return "";
        }
        try (InputStream input = s3Util.getObject(file.getObjectKey())) {
            if (input == null) {
                return "";
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(ResponseEnum.INTERNAL_SERVER_ERROR);
        }
    }

    private String readMultipartContent(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ResponseEnum.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean isSkillFile(String name) {
        return StringUtils.equalsIgnoreCase(name, SKILL_FILE_NAME);
    }

    private SkillMetadata extractSkillMetadata(String content, String fallbackName) {
        String name = null;
        String description = null;
        Matcher matcher = FRONTMATTER_PATTERN.matcher(StringUtils.defaultString(content));
        String body = content;
        if (matcher.find()) {
            String frontmatter = matcher.group(1);
            body = matcher.group(2);
            for (String line : frontmatter.split("\\r?\\n")) {
                String trimmed = StringUtils.trimToEmpty(line);
                if (trimmed.startsWith("name:")) {
                    name = cleanupYamlValue(StringUtils.substringAfter(trimmed, "name:"));
                } else if (trimmed.startsWith("description:")) {
                    description = cleanupYamlValue(StringUtils.substringAfter(trimmed, "description:"));
                }
            }
        }
        if (StringUtils.isBlank(name)) {
            for (String line : StringUtils.defaultString(body).split("\\r?\\n")) {
                String trimmed = StringUtils.trimToEmpty(line);
                if (trimmed.startsWith("#")) {
                    name = StringUtils.trimToEmpty(trimmed.replaceFirst("^#+", ""));
                    break;
                }
            }
        }
        if (StringUtils.isBlank(description)) {
            for (String line : StringUtils.defaultString(body).split("\\r?\\n")) {
                String trimmed = StringUtils.trimToEmpty(line);
                if (StringUtils.isNotBlank(trimmed) && !trimmed.startsWith("#")) {
                    description = trimmed;
                    break;
                }
            }
        }
        if (StringUtils.isBlank(name)) {
            name = StringUtils.defaultIfBlank(fallbackName, "skill");
        }
        return new SkillMetadata(
                StringUtils.left(name, 128),
                StringUtils.left(StringUtils.defaultString(description), 1024));
    }

    private String cleanupYamlValue(String raw) {
        String cleaned = StringUtils.trimToEmpty(raw);
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private boolean matchesSkillKeyword(SkillFile file, String keyword) {
        return StringUtils.containsIgnoreCase(resolveSkillName(file), keyword)
                || StringUtils.containsIgnoreCase(resolveSkillDescription(file), keyword)
                || StringUtils.containsIgnoreCase(parentFolderName(file), keyword);
    }

    private String resolveSkillName(SkillFile file) {
        if (file == null) {
            return "";
        }
        return StringUtils.defaultIfBlank(file.getSkillName(), parentFolderName(file));
    }

    private String resolveSkillDescription(SkillFile file) {
        if (file == null) {
            return "";
        }
        return StringUtils.defaultString(file.getSkillDescription());
    }

    private String parentFolderName(SkillFile file) {
        if (file == null || file.getParentId() == null || file.getParentId() == 0L) {
            return "";
        }
        SkillFile parent = getOne(scopeQuery().eq(SkillFile::getId, file.getParentId()), false);
        return parent == null ? "" : parent.getName();
    }

    private boolean isDescendant(Long candidateParentId, Long currentFolderId) {
        if (candidateParentId == null || candidateParentId == 0L) {
            return false;
        }
        SkillFile cursor = getOne(scopeQuery().eq(SkillFile::getId, candidateParentId), false);
        while (cursor != null && cursor.getParentId() != null && cursor.getParentId() != 0L) {
            if (Objects.equals(cursor.getParentId(), currentFolderId) || Objects.equals(cursor.getId(), currentFolderId)) {
                return true;
            }
            cursor = getOne(scopeQuery().eq(SkillFile::getId, cursor.getParentId()), false);
        }
        return cursor != null && Objects.equals(cursor.getId(), currentFolderId);
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null ? 0L : parentId;
    }

    private String currentUid() {
        return UserInfoManagerHandler.getUserId();
    }

    private Long currentSpaceId() {
        return SpaceInfoUtil.getSpaceId();
    }

    @Data
    @AllArgsConstructor
    private static class SkillMetadata {
        private String name;
        private String description;
    }
}
