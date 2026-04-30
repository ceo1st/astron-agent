package com.iflytek.astron.console.toolkit.handler;

import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astron.console.toolkit.entity.core.knowledge.QueryMatchObj;
import com.iflytek.astron.console.toolkit.entity.core.knowledge.QueryRequest;
import com.iflytek.astron.console.toolkit.entity.core.knowledge.SplitRequest;
import com.iflytek.astron.console.toolkit.util.OkHttpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/** Unit tests for {@link KnowledgeV2ServiceCallHandler}. */
class KnowledgeV2ServiceCallHandlerTest {

    private KnowledgeV2ServiceCallHandler handler;

    @BeforeEach
    void setUp() {
        handler = new KnowledgeV2ServiceCallHandler();
        ApiUrl apiUrl = mock(ApiUrl.class);
        when(apiUrl.getKnowledgeUrl()).thenReturn("http://core-knowledge.local");
        ReflectionTestUtils.setField(handler, "apiUrl", apiUrl);
    }

    @Test
    void applyDatasetIdToSplitRequest_setsForRagflowOnly() {
        SplitRequest req = new SplitRequest();
        req.setRagType("Ragflow-RAG");
        handler.applyDatasetIdToSplitRequest(req, "ds-1");
        assertThat(req.getDatasetId()).isEqualTo("ds-1");
    }

    @Test
    void applyDatasetIdToSplitRequest_noOpForNonRagflow() {
        SplitRequest req = new SplitRequest();
        req.setRagType("CBG-RAG");
        handler.applyDatasetIdToSplitRequest(req, "ds-1");
        assertThat(req.getDatasetId()).isNull();
    }

    @Test
    void applyDatasetIdToSplitRequest_noOpForBlankDatasetId() {
        SplitRequest req = new SplitRequest();
        req.setRagType("Ragflow-RAG");
        handler.applyDatasetIdToSplitRequest(req, "");
        assertThat(req.getDatasetId()).isNull();
        handler.applyDatasetIdToSplitRequest(req, null);
        assertThat(req.getDatasetId()).isNull();
    }

    @Test
    void applyDatasetIdToSplitRequest_nullRequestIsNoOp() {
        handler.applyDatasetIdToSplitRequest(null, "ds-1");
    }

    @Test
    void applyDatasetIdToUploadParams_setsForRagflowOnly() {
        Map<String, Object> params = new HashMap<>();
        handler.applyDatasetIdToUploadParams(params, "Ragflow-RAG", "ds-1");
        assertThat(params).containsEntry("datasetId", "ds-1");
    }

    @Test
    void applyDatasetIdToUploadParams_noOpForNonRagflow() {
        Map<String, Object> params = new HashMap<>();
        handler.applyDatasetIdToUploadParams(params, "CBG-RAG", "ds-1");
        assertThat(params).doesNotContainKey("datasetId");
    }

    @Test
    void applyDatasetIdToUploadParams_noOpForBlankDatasetId() {
        Map<String, Object> params = new HashMap<>();
        handler.applyDatasetIdToUploadParams(params, "Ragflow-RAG", "");
        assertThat(params).doesNotContainKey("datasetId");
        handler.applyDatasetIdToUploadParams(params, "Ragflow-RAG", null);
        assertThat(params).doesNotContainKey("datasetId");
    }

    @Test
    void applyDatasetIdToUploadParams_nullParamsIsNoOp() {
        handler.applyDatasetIdToUploadParams(null, "Ragflow-RAG", "ds-1");
    }

    @Test
    @DisplayName("createRagflowDataset returns datasetId on success")
    void createRagflowDataset_success() {
        try (MockedStatic<OkHttpUtil> okHttp = mockStatic(OkHttpUtil.class)) {
            okHttp.when(() -> OkHttpUtil.post(anyString(), anyString()))
                    .thenReturn("{\"code\":0,\"message\":\"success\",\"data\":{\"datasetId\":\"ds-abc-123\"}}");

            String result = handler.createRagflowDataset("uuid-name", "kb_alpha");

            assertThat(result).isEqualTo("ds-abc-123");
        }
    }

    @Test
    @DisplayName("createRagflowDataset throws on non-zero code")
    void createRagflowDataset_nonZeroCode() {
        try (MockedStatic<OkHttpUtil> okHttp = mockStatic(OkHttpUtil.class)) {
            okHttp.when(() -> OkHttpUtil.post(anyString(), anyString()))
                    .thenReturn("{\"code\":10003,\"message\":\"upstream failure\",\"data\":null}");

            assertThatThrownBy(() -> handler.createRagflowDataset("n", "d"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("createRagflowDataset throws when datasetId blank")
    void createRagflowDataset_blankDatasetId() {
        try (MockedStatic<OkHttpUtil> okHttp = mockStatic(OkHttpUtil.class)) {
            okHttp.when(() -> OkHttpUtil.post(anyString(), anyString()))
                    .thenReturn("{\"code\":0,\"message\":\"\",\"data\":{\"datasetId\":\"\"}}");

            assertThatThrownBy(() -> handler.createRagflowDataset("n", "d"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("createRagflowDataset throws on null/blank response")
    void createRagflowDataset_nullResponse() {
        try (MockedStatic<OkHttpUtil> okHttp = mockStatic(OkHttpUtil.class)) {
            okHttp.when(() -> OkHttpUtil.post(anyString(), anyString()))
                    .thenReturn("");

            assertThatThrownBy(() -> handler.createRagflowDataset("n", "d"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void knowledgeQuery_usesPlainPostWithoutInternalHeaders() {
        QueryRequest request = new QueryRequest();
        request.setQuery("hello");
        request.setTopN(3);
        request.setRagType("Ragflow-RAG");
        QueryMatchObj match = new QueryMatchObj();
        match.setRepoId(Collections.singletonList("repo-1"));
        match.setDatasetId(Collections.singletonList("ds-1"));
        request.setMatch(match);

        try (MockedStatic<OkHttpUtil> okHttp = mockStatic(OkHttpUtil.class)) {
            okHttp.when(() -> OkHttpUtil.post(
                    eq("http://core-knowledge.local/v1/chunk/query"),
                    anyString()))
                    .thenReturn("{\"code\":0,\"message\":\"success\",\"data\":{\"results\":[]}}");

            assertThat(handler.knowledgeQuery(request).getCode()).isZero();
            okHttp.verify(() -> OkHttpUtil.post(
                    eq("http://core-knowledge.local/v1/chunk/query"),
                    anyMap(),
                    anyString()), never());
        }
    }
}
