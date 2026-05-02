package com.iflytek.astron.console.toolkit.service.workflow;

import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astron.console.toolkit.entity.table.workflow.WorkflowAutomationRun;
import com.iflytek.astron.console.toolkit.entity.table.workflow.WorkflowAutomationTask;
import com.iflytek.astron.console.toolkit.mapper.workflow.WorkflowAutomationRunMapper;
import com.iflytek.astron.console.toolkit.mapper.workflow.WorkflowAutomationTaskMapper;
import com.iflytek.astron.console.toolkit.mapper.workflow.WorkflowMapper;
import com.iflytek.astron.console.toolkit.service.extra.AppService;
import com.iflytek.astron.console.toolkit.tool.DataPermissionCheckTool;
import com.iflytek.astron.console.toolkit.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowAutomationServiceTest {

    @Mock
    private WorkflowAutomationTaskMapper taskMapper;
    @Mock
    private WorkflowAutomationRunMapper runMapper;
    @Mock
    private WorkflowMapper workflowMapper;
    @Mock
    private DataPermissionCheckTool dataPermissionCheckTool;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private ApiUrl apiUrl;
    @Mock
    private AppService appService;

    @Spy
    @InjectMocks
    private WorkflowAutomationService service;

    @Test
    void previewReturnsFiveFutureFireTimes() {
        List<String> result = service.preview("0 0 9 * * ?", "Asia/Shanghai");

        assertThat(result).hasSize(5);
        assertThat(result).allSatisfy(time -> assertThat(time).isNotBlank());
    }

    @Test
    void previewRejectsInvalidCron() {
        assertThatThrownBy(() -> service.preview("not a cron", "Asia/Shanghai"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void runTaskWritesSkippedWhenLockIsBusy() {
        WorkflowAutomationTask task = new WorkflowAutomationTask();
        task.setId(12L);
        task.setFlowId("flow-1");
        task.setUid("u1");
        task.setEnabled(true);
        task.setCronExpression("0 0/5 * * * ?");
        task.setTimezone("Asia/Shanghai");
        task.setInputParams("{}");

        when(redisUtil.tryLock(anyString(), anyLong(), anyString())).thenReturn(false);
        doReturn(true).when(service).updateById(any(WorkflowAutomationTask.class));

        WorkflowAutomationRun run = service.runTask(task, "SCHEDULE", new Date());

        assertThat(run.getStatus()).isEqualTo("SKIPPED");
        assertThat(task.getLastRunStatus()).isEqualTo("SKIPPED");
        assertThat(task.getNextFireTime()).isNotNull();
        ArgumentCaptor<WorkflowAutomationRun> captor = ArgumentCaptor.forClass(WorkflowAutomationRun.class);
        verify(runMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SKIPPED");
        verify(redisUtil).tryLock(eq("workflow:automation:task:12:lock"), eq(3600L), anyString());
    }
}
