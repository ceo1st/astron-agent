package com.iflytek.astron.console.toolkit.handler;

import com.iflytek.astron.console.toolkit.entity.core.knowledge.SplitRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link KnowledgeV2ServiceCallHandler} {@code group} and {@code groupDescription}
 * forwarding (Ragflow-RAG only).
 */
class KnowledgeV2ServiceCallHandlerTest {

    // The package-private helpers below are decorators on request/params; we
    // exercise them directly to avoid mocking OkHttpUtil static methods.

    @Test
    void documentSplit_RagflowRAG_withCoreRepoId_setsGroupOnRequest() {
        KnowledgeV2ServiceCallHandler handler = new KnowledgeV2ServiceCallHandler();
        SplitRequest req = new SplitRequest();
        req.setRagType("Ragflow-RAG");
        handler.applyGroupToSplitRequest(req, "abc-uuid");
        assertEquals("abc-uuid", req.getGroup());
    }

    @Test
    void documentSplit_NonRagflowRAG_doesNotSetGroup() {
        KnowledgeV2ServiceCallHandler handler = new KnowledgeV2ServiceCallHandler();
        SplitRequest req = new SplitRequest();
        req.setRagType("CBG-RAG");
        handler.applyGroupToSplitRequest(req, "abc-uuid");
        assertNull(req.getGroup());
    }

    @Test
    void documentSplit_RagflowRAG_withNullCoreRepoId_doesNotSetGroup() {
        KnowledgeV2ServiceCallHandler handler = new KnowledgeV2ServiceCallHandler();
        SplitRequest req = new SplitRequest();
        req.setRagType("Ragflow-RAG");
        handler.applyGroupToSplitRequest(req, null);
        assertNull(req.getGroup());
    }

    @Test
    void applyGroupToUploadParams_RagflowRAG_withCoreRepoId_addsGroup() {
        KnowledgeV2ServiceCallHandler handler = new KnowledgeV2ServiceCallHandler();
        Map<String, Object> params = new HashMap<>();
        handler.applyGroupToUploadParams(params, "Ragflow-RAG", "abc-uuid");
        assertEquals("abc-uuid", params.get("group"));
    }

    @Test
    void applyGroupToUploadParams_NonRagflowRAG_doesNotAddGroup() {
        KnowledgeV2ServiceCallHandler handler = new KnowledgeV2ServiceCallHandler();
        Map<String, Object> params = new HashMap<>();
        handler.applyGroupToUploadParams(params, "CBG-RAG", "abc-uuid");
        assertNull(params.get("group"));
    }

    @Test
    void applyGroupToUploadParams_RagflowRAG_withBlankCoreRepoId_doesNotAddGroup() {
        KnowledgeV2ServiceCallHandler handler = new KnowledgeV2ServiceCallHandler();
        Map<String, Object> params = new HashMap<>();
        handler.applyGroupToUploadParams(params, "Ragflow-RAG", "");
        assertNull(params.get("group"));
    }

    @Test
    void applyGroupDescriptionToSplitRequest_RagflowRAG_withRepoName_setsDescription() {
        KnowledgeV2ServiceCallHandler handler = new KnowledgeV2ServiceCallHandler();
        SplitRequest req = new SplitRequest();
        req.setRagType("Ragflow-RAG");
        handler.applyGroupDescriptionToSplitRequest(req, "客服知识库");
        assertEquals("客服知识库", req.getGroupDescription());
    }

    @Test
    void applyGroupDescriptionToSplitRequest_NonRagflowRAG_doesNotSetDescription() {
        KnowledgeV2ServiceCallHandler handler = new KnowledgeV2ServiceCallHandler();
        SplitRequest req = new SplitRequest();
        req.setRagType("CBG-RAG");
        handler.applyGroupDescriptionToSplitRequest(req, "客服知识库");
        assertNull(req.getGroupDescription());
    }

    @Test
    void applyGroupDescriptionToUploadParams_RagflowRAG_withRepoName_addsDescription() {
        KnowledgeV2ServiceCallHandler handler = new KnowledgeV2ServiceCallHandler();
        Map<String, Object> params = new HashMap<>();
        handler.applyGroupDescriptionToUploadParams(params, "Ragflow-RAG", "客服知识库");
        assertEquals("客服知识库", params.get("groupDescription"));
    }

    @Test
    void applyGroupDescriptionToUploadParams_RagflowRAG_withBlankRepoName_doesNotAdd() {
        KnowledgeV2ServiceCallHandler handler = new KnowledgeV2ServiceCallHandler();
        Map<String, Object> params = new HashMap<>();
        handler.applyGroupDescriptionToUploadParams(params, "Ragflow-RAG", "");
        assertNull(params.get("groupDescription"));
    }
}
