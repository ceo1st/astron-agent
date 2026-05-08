package com.iflytek.astron.console.commons.workflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.entity.chat.ChatReqRecords;
import com.iflytek.astron.console.commons.service.ChatRecordModelService;
import com.iflytek.astron.console.commons.service.WssListenerService;
import okhttp3.sse.EventSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for WorkflowListener to verify end node outputs handling
 * in both debug and published workflow modes.
 *
 * @author mingsuiyongheng
 */
@ExtendWith(MockitoExtension.class)
class WorkflowListenerTest {

    @Mock
    private WorkflowClient workflowClient;

    @Mock
    private WssListenerService wssListenerService;

    @Mock
    private ChatRecordModelService chatRecordModelService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private SseEmitter sseEmitter;

    @Mock
    private EventSource eventSource;

    private ChatReqRecords chatReqRecords;
    private String sseId;

    @BeforeEach
    void setUp() {
        sseId = "test-sse-id";
        chatReqRecords = new ChatReqRecords();
        chatReqRecords.setId(1L);
        chatReqRecords.setChatId(100L);
        chatReqRecords.setUid("test-user");
        chatReqRecords.setMessage("test question");
        chatReqRecords.setCreateTime(LocalDateTime.now());

        when(wssListenerService.getChatRecordModelService()).thenReturn(chatRecordModelService);
    }

    /**
     * Test that end node outputs are correctly appended to finalResult
     * when isDebug=false (published workflow mode) and answer_mode=0
     */
    @Test
    void testEndNodeOutputsInPublishedMode() {
        // Given: A published workflow listener (isDebug=false)
        WorkflowListener listener = new WorkflowListener(
                workflowClient,
                chatReqRecords,
                sseId,
                wssListenerService,
                false, // isDebug = false (published mode)
                sseEmitter
        );

        // Create SSE data with end node outputs (answer_mode=0)
        JSONObject sseData = createEndNodeSseData();

        // When: Processing the SSE event
        listener.onEvent(eventSource, "1", "message", sseData.toJSONString());

        // Then: Verify finalResult contains the outputs JSON
        StringBuffer finalResult = listener.getFinalResult();
        assertNotNull(finalResult, "finalResult should not be null");
        assertTrue(finalResult.length() > 0, "finalResult should contain end node outputs");

        // Verify the outputs are correctly serialized
        String resultStr = finalResult.toString();
        assertTrue(resultStr.contains("\"id\""), "Result should contain 'id' field");
        assertTrue(resultStr.contains("\"uid\""), "Result should contain 'uid' field");
        assertTrue(resultStr.contains("\"name\""), "Result should contain 'name' field");
        assertTrue(resultStr.contains("\"create_time\""), "Result should contain 'create_time' field");

        // Verify it's valid JSON
        assertDoesNotThrow(() -> JSON.parseObject(resultStr),
                "Result should be valid JSON");
    }

    /**
     * Test that end node outputs are correctly appended to finalResult
     * when isDebug=true (debug workflow mode) and answer_mode=0
     */
    @Test
    void testEndNodeOutputsInDebugMode() {
        // Given: A debug workflow listener (isDebug=true)
        WorkflowListener listener = new WorkflowListener(
                workflowClient,
                chatReqRecords,
                sseId,
                wssListenerService,
                true, // isDebug = true (debug mode)
                sseEmitter
        );

        // Create SSE data with end node outputs (answer_mode=0)
        JSONObject sseData = createEndNodeSseData();

        // When: Processing the SSE event
        listener.onEvent(eventSource, "1", "message", sseData.toJSONString());

        // Then: Verify finalResult contains the outputs JSON
        StringBuffer finalResult = listener.getFinalResult();
        assertNotNull(finalResult, "finalResult should not be null");
        assertTrue(finalResult.length() > 0, "finalResult should contain end node outputs");

        // Verify the outputs are correctly serialized
        String resultStr = finalResult.toString();
        assertTrue(resultStr.contains("\"id\""), "Result should contain 'id' field");
        assertTrue(resultStr.contains("\"uid\""), "Result should contain 'uid' field");
    }

    /**
     * Test that streaming content (answer_mode=1) is handled correctly
     * and outputs are NOT appended
     */
    @Test
    void testStreamingContentNotAppendedAsOutputs() {
        // Given: A published workflow listener
        WorkflowListener listener = new WorkflowListener(
                workflowClient,
                chatReqRecords,
                sseId,
                wssListenerService,
                false,
                sseEmitter
        );

        // Create SSE data with streaming content (answer_mode=1)
        JSONObject sseData = createStreamingSseData();

        // When: Processing the SSE event
        listener.onEvent(eventSource, "1", "message", sseData.toJSONString());

        // Then: Verify finalResult contains streaming content, not outputs JSON
        StringBuffer finalResult = listener.getFinalResult();
        String resultStr = finalResult.toString();

        // Should contain the streaming content
        assertTrue(resultStr.contains("This is streaming response"),
                "Result should contain streaming content");

        // Should NOT contain the outputs structure
        assertFalse(resultStr.contains("\"outputs\""),
                "Result should not contain outputs JSON structure for streaming mode");
    }

    /**
     * Test that non-end nodes do not trigger outputs appending
     */
    @Test
    void testNonEndNodeDoesNotAppendOutputs() {
        // Given: A published workflow listener
        WorkflowListener listener = new WorkflowListener(
                workflowClient,
                chatReqRecords,
                sseId,
                wssListenerService,
                false,
                sseEmitter
        );

        // Create SSE data with a message node (not end node)
        JSONObject sseData = createMessageNodeSseData();

        // When: Processing the SSE event
        listener.onEvent(eventSource, "1", "message", sseData.toJSONString());

        // Then: Verify finalResult contains streaming content only
        StringBuffer finalResult = listener.getFinalResult();
        String resultStr = finalResult.toString();

        assertEquals("Message node content", resultStr,
                "Result should only contain message node streaming content");
    }

    // Helper methods to create test SSE data

    private JSONObject createEndNodeSseData() {
        JSONObject data = new JSONObject();
        data.put("code", 0);
        data.put("id", "test-sid-123");
        data.put("message", "Success");

        // Create choices array
        JSONObject choice = new JSONObject();
        JSONObject delta = new JSONObject();
        delta.put("content", "");
        choice.put("delta", delta);
        choice.put("finish_reason", "stop");
        data.put("choices", JSON.parseArray("[" + choice.toJSONString() + "]"));

        // Create workflow_step with end node
        JSONObject workflowStep = new JSONObject();
        JSONObject node = new JSONObject();
        node.put("id", "node-end-1");
        node.put("finish_reason", "stop");

        // Set answer_mode=0 (non-streaming, direct JSON output)
        JSONObject ext = new JSONObject();
        ext.put("answer_mode", 0);
        node.put("ext", ext);

        // Create outputs
        JSONObject outputs = new JSONObject();
        outputs.put("id", "12345");
        outputs.put("uid", "user-001");
        outputs.put("name", "Test User");
        outputs.put("create_time", "2024-01-01 10:00:00");
        node.put("outputs", outputs);

        workflowStep.put("node", node);
        data.put("workflow_step", workflowStep);

        return data;
    }

    private JSONObject createStreamingSseData() {
        JSONObject data = new JSONObject();
        data.put("code", 0);
        data.put("id", "test-sid-456");

        // Create choices with streaming content
        JSONObject choice = new JSONObject();
        JSONObject delta = new JSONObject();
        delta.put("content", "This is streaming response");
        choice.put("delta", delta);
        choice.put("finish_reason", "stop");
        data.put("choices", JSON.parseArray("[" + choice.toJSONString() + "]"));

        // Create workflow_step with end node but answer_mode=1 (streaming)
        JSONObject workflowStep = new JSONObject();
        JSONObject node = new JSONObject();
        node.put("id", "node-end-2");
        node.put("finish_reason", "stop");

        JSONObject ext = new JSONObject();
        ext.put("answer_mode", 1); // Streaming mode
        node.put("ext", ext);

        JSONObject outputs = new JSONObject();
        outputs.put("result", "should not be appended");
        node.put("outputs", outputs);

        workflowStep.put("node", node);
        data.put("workflow_step", workflowStep);

        return data;
    }

    private JSONObject createMessageNodeSseData() {
        JSONObject data = new JSONObject();
        data.put("code", 0);
        data.put("id", "test-sid-789");

        // Create choices with streaming content
        JSONObject choice = new JSONObject();
        JSONObject delta = new JSONObject();
        delta.put("content", "Message node content");
        choice.put("delta", delta);
        choice.put("finish_reason", "stop");
        data.put("choices", JSON.parseArray("[" + choice.toJSONString() + "]"));

        // Create workflow_step with message node (not end node)
        JSONObject workflowStep = new JSONObject();
        JSONObject node = new JSONObject();
        node.put("id", "message-node-1"); // Not an end node
        node.put("finish_reason", "stop");

        JSONObject ext = new JSONObject();
        ext.put("answer_mode", 0);
        node.put("ext", ext);

        workflowStep.put("node", node);
        data.put("workflow_step", workflowStep);

        return data;
    }
}
