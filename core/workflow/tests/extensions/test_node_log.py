from workflow.extensions.otlp.log_trace.node_log import NodeLog


def test_node_log_stores_input_variables_without_dynamic_fields():
    node_log = NodeLog(sid="sid")

    node_log.append_input_data("date", "2026-04-28")
    node_log.append_input_data("custom_name", {"text": "中文"})

    assert node_log.data.input == {}
    assert [item.name for item in node_log.data.input_vars] == ["date", "custom_name"]
    assert node_log.data.input_vars[0].value == "2026-04-28"
    assert node_log.data.input_vars[1].value == '{"text": "中文"}'


def test_node_log_stores_output_variables_without_dynamic_fields():
    node_log = NodeLog(sid="sid")

    node_log.append_output_data("answer", ["ok"])

    assert node_log.data.output == {}
    assert node_log.data.output_vars[0].name == "answer"
    assert node_log.data.output_vars[0].value == '["ok"]'
