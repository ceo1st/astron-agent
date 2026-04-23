package com.iflytek.astron.console.toolkit.controller.skill;

import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astron.console.toolkit.entity.dto.skill.SkillDirectoryUploadResultDto;
import com.iflytek.astron.console.toolkit.entity.dto.skill.SkillFileContentDto;
import com.iflytek.astron.console.toolkit.entity.dto.skill.SkillFileTreeNodeDto;
import com.iflytek.astron.console.toolkit.entity.dto.skill.SkillImportDto;
import com.iflytek.astron.console.toolkit.entity.vo.skill.SkillFileCreateFolderReq;
import com.iflytek.astron.console.toolkit.entity.vo.skill.SkillFileCreateReq;
import com.iflytek.astron.console.toolkit.entity.vo.skill.SkillFileMoveReq;
import com.iflytek.astron.console.toolkit.entity.vo.skill.SkillFileRenameReq;
import com.iflytek.astron.console.toolkit.entity.vo.skill.SkillFileUpdateContentReq;
import com.iflytek.astron.console.toolkit.service.skill.SkillFileService;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/skill-file")
@ResponseResultBody
public class SkillFileController {

    @Resource
    private SkillFileService skillFileService;

    @GetMapping("/tree")
    public ApiResult<List<SkillFileTreeNodeDto>> tree(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResult.success(skillFileService.listTree(keyword));
    }

    @GetMapping("/content")
    public ApiResult<SkillFileContentDto> content(@RequestParam("id") Long id) {
        return ApiResult.success(skillFileService.getContent(id));
    }

    @PostMapping("/folder")
    public ApiResult<SkillFileTreeNodeDto> createFolder(@RequestBody SkillFileCreateFolderReq req) {
        return ApiResult.success(skillFileService.createFolder(req));
    }

    @PostMapping("/file")
    public ApiResult<SkillFileContentDto> createFile(@RequestBody SkillFileCreateReq req) {
        return ApiResult.success(skillFileService.createFile(req));
    }

    @PostMapping("/upload")
    public ApiResult<List<SkillFileContentDto>> upload(
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestPart("files") MultipartFile[] files) {
        return ApiResult.success(skillFileService.uploadFiles(parentId, files));
    }

    @PostMapping("/upload-directory")
    public ApiResult<SkillDirectoryUploadResultDto> uploadDirectory(
            @RequestParam("paths") List<String> paths,
            @RequestPart("files") MultipartFile[] files) {
        return ApiResult.success(skillFileService.uploadDirectory(paths, files));
    }

    @PutMapping("/content")
    public ApiResult<SkillFileContentDto> updateContent(
            @RequestBody SkillFileUpdateContentReq req) {
        return ApiResult.success(skillFileService.updateContent(req));
    }

    @PutMapping("/rename")
    public ApiResult<SkillFileTreeNodeDto> rename(@RequestBody SkillFileRenameReq req) {
        return ApiResult.success(skillFileService.rename(req));
    }

    @PutMapping("/move")
    public ApiResult<SkillFileTreeNodeDto> move(@RequestBody SkillFileMoveReq req) {
        return ApiResult.success(skillFileService.move(req));
    }

    @DeleteMapping
    public ApiResult<Void> delete(@RequestParam("id") Long id) {
        skillFileService.delete(id);
        return ApiResult.success();
    }

    @GetMapping("/importable")
    public ApiResult<List<SkillImportDto>> importable(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResult.success(skillFileService.listImportableSkills(keyword));
    }
}
