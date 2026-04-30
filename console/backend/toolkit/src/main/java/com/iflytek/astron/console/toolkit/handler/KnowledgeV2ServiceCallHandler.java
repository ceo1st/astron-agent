package com.iflytek.astron.console.toolkit.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.common.constant.ProjectContent;
import com.iflytek.astron.console.toolkit.config.properties.RepoAuthorizedConfig;
import com.iflytek.astron.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astron.console.toolkit.entity.core.knowledge.*;
import com.iflytek.astron.console.toolkit.util.OkHttpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class KnowledgeV2ServiceCallHandler {
    @Resource
    private ApiUrl apiUrl;
    @Resource
    private RepoAuthorizedConfig repoAuthorizedConfig;

    private static final String DATASET_ID_FIELD = "datasetId";

    /**
     * Create or reuse a RAGFlow dataset and return its id.
     *
     * @param name RAGFlow dataset.name
     * @param description RAGFlow dataset.description; nullable
     */
    public String createRagflowDataset(String name, String description) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/dataset/create");
        DatasetCreateRequest req = new DatasetCreateRequest(name, description);
        String reqBody = JSON.toJSONString(req);
        log.info("createRagflowDataset url = {}, name = {}", url, name);
        String resp = postJson(url, reqBody);
        log.info("createRagflowDataset response = {}", resp);
        KnowledgeResponse parsed = JSON.parseObject(resp, KnowledgeResponse.class);
        if (parsed == null || parsed.getCode() == null || parsed.getCode() != 0) {
            String msg = (parsed == null) ? "blank response" : parsed.getMessage();
            throw new BusinessException(ResponseEnum.REPO_CREATE_RAGFLOW_FAILED, msg);
        }
        return extractDatasetId(parsed.getData());
    }

    private String extractDatasetId(Object data) {
        JSONObject dataObj = toJsonObject(data);
        String datasetId = dataObj == null ? null : dataObj.getString(DATASET_ID_FIELD);
        if (StringUtils.isBlank(datasetId)) {
            throw new BusinessException(ResponseEnum.REPO_CREATE_RAGFLOW_FAILED,
                    "RAGFlow returned blank datasetId");
        }
        return datasetId;
    }

    private JSONObject toJsonObject(Object data) {
        if (data instanceof JSONObject) {
            return (JSONObject) data;
        }
        if (data instanceof String) {
            return JSON.parseObject((String) data);
        }
        throw new BusinessException(ResponseEnum.REPO_CREATE_RAGFLOW_FAILED,
                "RAGFlow returned non-object data");
    }

    /**
     * Document parsing and chunking
     *
     * @param request the split request describing the document
     * @param datasetId RAGFlow dataset.id; ignored when blank or non-Ragflow
     * @return knowledge response from the upstream split API
     */
    public KnowledgeResponse documentSplit(SplitRequest request, String datasetId) {
        applyDatasetIdToSplitRequest(request, datasetId);
        String url = apiUrl.getKnowledgeUrl().concat("/v1/document/split");
        String reqBody = JSON.toJSONString(request);
        log.info("documentSplit url = {}, request = {}", url, reqBody);
        String post = postJson(url, reqBody);
        log.info("documentSplit response = {}", post);
        return JSON.parseObject(post, KnowledgeResponse.class);
    }

    /**
     * Document upload and chunking (multipart/form-data)
     *
     * @param multipartFile multipart file to upload
     * @param lengthRange chunking length range
     * @param separator separator list
     * @param ragType RAG type
     * @param resourceType resource type (0=file, 1=html)
     * @param oldDocId existing RAGFlow doc id for upsert; null for first slice
     * @param datasetId RAGFlow dataset.id; ignored when blank or non-Ragflow
     * @return KnowledgeResponse
     */
    public KnowledgeResponse documentUpload(MultipartFile multipartFile,
            List<Integer> lengthRange, List<String> separator,
            String ragType, Integer resourceType,
            String oldDocId,
            String datasetId) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/document/upload");

        try {
            log.info("documentUpload fileName: {}, fileSize: {} bytes, oldDocId: {}",
                    multipartFile.getOriginalFilename(), multipartFile.getSize(), oldDocId);

            Map<String, Object> params = new HashMap<>();
            params.put("file", multipartFile);
            if (lengthRange != null) {
                params.put("lengthRange", JSON.toJSONString(lengthRange));
            }
            if (separator != null && !separator.isEmpty()) {
                params.put("separator", JSON.toJSONString(separator));
            }
            params.put("ragType", ragType);
            if (resourceType != null) {
                params.put("resourceType", resourceType.toString());
            }
            if (StringUtils.isNotBlank(oldDocId)) {
                params.put("documentId", oldDocId);
            }
            applyDatasetIdToUploadParams(params, ragType, datasetId);

            log.info("documentUpload url = {}, ragType = {}, resourceType = {}", url, ragType, resourceType);
            String post = OkHttpUtil.postMultipart(url, null, null, params, null);
            log.info("documentUpload response = {}", post);
            return JSON.parseObject(post, KnowledgeResponse.class);
        } catch (Exception e) {
            log.error("documentUpload error: {}", e.getMessage(), e);
            KnowledgeResponse errorResponse = new KnowledgeResponse();
            errorResponse.setCode(-1);
            errorResponse.setMessage("Upload failed: " + e.getMessage());
            return errorResponse;
        }
    }

    void applyDatasetIdToSplitRequest(SplitRequest request, String datasetId) {
        if (request == null) {
            return;
        }
        if (ProjectContent.FILE_SOURCE_RAG_FLOW_RAG_STR.equals(request.getRagType())
                && StringUtils.isNotBlank(datasetId)) {
            request.setDatasetId(datasetId);
        }
    }

    void applyDatasetIdToUploadParams(
            Map<String, Object> params, String ragType, String datasetId) {
        if (params == null) {
            return;
        }
        if (ProjectContent.FILE_SOURCE_RAG_FLOW_RAG_STR.equals(ragType)
                && StringUtils.isNotBlank(datasetId)) {
            params.put(DATASET_ID_FIELD, datasetId);
        }
    }

    public KnowledgeResponse saveChunk(KnowledgeRequest request) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/chunks/save");
        String reqBody = JSON.toJSONString(request);
        log.info("saveChunk url = {}, request = {}", url, reqBody);
        String post = postJson(url, reqBody);
        log.info("saveChunk response = {}", post);
        return JSON.parseObject(post, KnowledgeResponse.class);
    }

    public KnowledgeResponse updateChunk(KnowledgeRequest request) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/chunk/update");
        String reqBody = JSON.toJSONString(request);
        log.info("updateChunk url = {}, request = {}", url, reqBody);
        String post = postJson(url, reqBody);
        log.info("updateChunk response = {}", post);
        return JSON.parseObject(post, KnowledgeResponse.class);
    }

    public KnowledgeResponse deleteDocOrChunk(KnowledgeRequest request) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/chunk/delete");
        String reqBody = JSON.toJSONString(request);
        log.info("deleteDocOrChunk url = {}, request = {}", url, reqBody);
        String post = postJson(url, reqBody);
        log.info("deleteDocOrChunk response = {}", post);
        return JSON.parseObject(post, KnowledgeResponse.class);
    }

    public KnowledgeResponse knowledgeQuery(QueryRequest request) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/chunk/query");
        String reqBody = JSON.toJSONString(request);
        log.info("knowledgeQuery request url:{}\ndata:{}", url, reqBody);
        String respData = postJson(url, reqBody);
        log.info("knowledgeQuery response data:{}", respData);
        return JSON.parseObject(respData, KnowledgeResponse.class);
    }

    private String postJson(String url, String reqBody) {
        return OkHttpUtil.post(url, reqBody);
    }
}
