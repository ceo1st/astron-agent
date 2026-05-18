import json

import pytest

from workflow.engine.nodes.code.code_node import CodeNode
from workflow.engine.nodes.code.executor.base_executor import CodeExecutorFactory


class DummySpan:
    async def add_info_event_async(self, _event):
        return None

    async def add_info_events_async(self, _events):
        return None

    def record_exception(self, _error):
        return None


class RecordingExecutor:
    def __init__(self):
        self.kwargs = None

    async def execute(self, language, code, timeout, span, **kwargs):
        self.kwargs = kwargs
        return json.dumps({"result": "ok"}, ensure_ascii=False)


@pytest.mark.asyncio
async def test_code_node_uses_e2b_when_runtime_sandbox_is_configured(monkeypatch):
    executor = RecordingExecutor()
    requested_types = []

    def fake_create_executor(executor_type):
        requested_types.append(executor_type)
        return executor

    monkeypatch.setenv("CODE_EXEC_TYPE", "local")
    monkeypatch.setattr(CodeExecutorFactory, "create_executor", fake_create_executor)

    node = CodeNode(
        codeLanguage="python",
        input_identifier=[],
        output_identifier=["result"],
        code="def main():\n    return {'result': 'ok'}",
        appId="app-1",
        uid="user-1",
        node_id="ifly-code::node-1",
        sandbox={
            "provider": "e2b",
            "enabled": True,
            "apiKey": "secret",
            "timeoutSeconds": 90,
            "allowInternetAccess": True,
            "artifactUploadUrl": "http://hub/workflow/artifacts/internal-upload",
            "artifactUploadToken": "token",
            "workflowId": "flow-1",
            "runId": "run-1",
            "nodeId": "ifly-code::node-1",
            "uid": "user-1",
            "spaceId": "100",
        },
    )

    result = await node.execute_code({}, DummySpan())

    assert result == {"result": "ok"}
    assert requested_types == ["e2b"]
    assert executor.kwargs["sandbox"]["api_key"] == "secret"
    assert executor.kwargs["sandbox"]["workflow_id"] == "flow-1"
    assert executor.kwargs["sandbox"]["run_id"] == "run-1"
    assert executor.kwargs["sandbox"]["node_id"] == "ifly-code::node-1"
    assert executor.kwargs["sandbox"]["space_id"] == "100"


@pytest.mark.asyncio
async def test_code_node_falls_back_to_configured_executor_without_sandbox(monkeypatch):
    executor = RecordingExecutor()
    requested_types = []

    def fake_create_executor(executor_type):
        requested_types.append(executor_type)
        return executor

    monkeypatch.setenv("CODE_EXEC_TYPE", "langchain")
    monkeypatch.setattr(CodeExecutorFactory, "create_executor", fake_create_executor)

    node = CodeNode(
        codeLanguage="python",
        input_identifier=[],
        output_identifier=["result"],
        code="def main():\n    return {'result': 'ok'}",
        appId="app-1",
        uid="user-1",
        node_id="ifly-code::node-1",
    )

    await node.execute_code({}, DummySpan())

    assert requested_types == ["langchain"]
    assert executor.kwargs["sandbox"] is None
