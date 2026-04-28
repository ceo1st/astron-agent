#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Tests for RagflowRAGStrategy.query() multi-repo dataset routing."""

import logging
from unittest.mock import AsyncMock, patch

import pytest

from knowledge.domain.entity.chunk_dto import RagflowQueryExt
from knowledge.exceptions.exception import ThirdPartyException
from knowledge.service.impl.ragflow_strategy import RagflowRAGStrategy

_GET_DATASET_NAME = (
    "knowledge.service.impl.ragflow_strategy.RagflowUtils.get_default_dataset_name"
)
_GET_DATASET_ID = (
    "knowledge.service.impl.ragflow_strategy.RagflowUtils.get_dataset_id_by_name"
)
_RETRIEVAL = (
    "knowledge.service.impl.ragflow_strategy.ragflow_client.retrieval_with_dataset"
)
_CONVERT = (
    "knowledge.service.impl.ragflow_strategy."
    "RagflowUtils.convert_ragflow_query_response"
)


# ---------------------------------------------------------------------------
# _resolve_query_datasets helper
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_resolve_query_datasets_no_repo_ids_uses_default() -> None:
    """repo_ids=None falls back to default group dataset."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_NAME, return_value="default-group"), patch(
        _GET_DATASET_ID, new=AsyncMock(return_value="ds-default")
    ):
        dataset_ids, missing = await strategy._resolve_query_datasets(None)
        assert dataset_ids == ["ds-default"]
        assert missing == []


@pytest.mark.asyncio
async def test_resolve_query_datasets_no_repo_ids_default_missing() -> None:
    """Default dataset missing returns ([], [])."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_NAME, return_value="default-group"), patch(
        _GET_DATASET_ID, new=AsyncMock(return_value=None)
    ):
        dataset_ids, missing = await strategy._resolve_query_datasets(None)
        assert dataset_ids == []
        assert missing == []


@pytest.mark.asyncio
async def test_resolve_query_datasets_empty_list_uses_default() -> None:
    """Empty repo_ids list also falls to default."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_NAME, return_value="default-group"), patch(
        _GET_DATASET_ID, new=AsyncMock(return_value="ds-default")
    ):
        dataset_ids, missing = await strategy._resolve_query_datasets([])
        assert dataset_ids == ["ds-default"]
        assert missing == []


@pytest.mark.asyncio
async def test_resolve_query_datasets_single_repo_present() -> None:
    """Single repo_ids, dataset exists."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_ID, new=AsyncMock(return_value="ds-abc")):
        dataset_ids, missing = await strategy._resolve_query_datasets(["abc-uuid"])
        assert dataset_ids == ["ds-abc"]
        assert missing == []


@pytest.mark.asyncio
async def test_resolve_query_datasets_multi_repo_partial_missing() -> None:
    """Multiple repo_ids, some datasets missing."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_ID, new=AsyncMock(side_effect=["ds-abc", None])):
        dataset_ids, missing = await strategy._resolve_query_datasets(
            ["abc-uuid", "def-not-exist"]
        )
        assert dataset_ids == ["ds-abc"]
        assert missing == ["def-not-exist"]


@pytest.mark.asyncio
async def test_resolve_query_datasets_all_missing() -> None:
    """All repo_ids datasets missing returns ([], [...all])."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_ID, new=AsyncMock(side_effect=[None, None])):
        dataset_ids, missing = await strategy._resolve_query_datasets(["xxx", "yyy"])
        assert dataset_ids == []
        assert missing == ["xxx", "yyy"]


# ---------------------------------------------------------------------------
# _build_retrieval_payload + _apply_ragflow_ext helpers
# ---------------------------------------------------------------------------


def test_build_retrieval_payload_basic() -> None:
    """Payload contains dataset_ids, question, top_k, similarity_threshold."""
    strategy = RagflowRAGStrategy()
    payload = strategy._build_retrieval_payload(
        query="hello",
        dataset_ids=["ds-1", "ds-2"],
        doc_ids=None,
        top_k=5,
        threshold=0.3,
        ext=None,
    )
    assert payload["dataset_ids"] == ["ds-1", "ds-2"]
    assert payload["question"] == "hello"
    assert payload["top_k"] == 5
    assert payload["similarity_threshold"] == 0.3
    assert payload["vector_similarity_weight"] == 0.2


def test_build_retrieval_payload_with_doc_ids_double_filter() -> None:
    """Payload includes both dataset_ids and document_ids when doc_ids given."""
    strategy = RagflowRAGStrategy()
    payload = strategy._build_retrieval_payload(
        query="hello",
        dataset_ids=["ds-1"],
        doc_ids=["doc1", "doc2"],
        top_k=5,
        threshold=0.0,
        ext=None,
    )
    assert payload["dataset_ids"] == ["ds-1"]
    assert payload["document_ids"] == ["doc1", "doc2"]


def test_apply_ragflow_ext_sets_keyword_and_other_fields() -> None:
    """_apply_ragflow_ext sets non-top_k fields on payload."""
    strategy = RagflowRAGStrategy()
    payload = {"top_k": 5, "question": "x"}
    ext = RagflowQueryExt(top_k=20, keyword=True)
    strategy._apply_ragflow_ext(payload, ext)
    assert payload["top_k"] == 5  # unchanged: helper does NOT touch top_k
    assert payload["keyword"] is True


def test_apply_ragflow_ext_highlight_str_workaround() -> None:
    """v0.20.5 workaround: highlight is converted to str(bool)."""
    strategy = RagflowRAGStrategy()
    payload: dict = {}
    ext = RagflowQueryExt(highlight=False)
    strategy._apply_ragflow_ext(payload, ext)
    # v0.20.5 highlight string-comparison bug: must pass str(False) = "False".
    assert payload["highlight"] == "False"


# ---------------------------------------------------------------------------
# _execute_retrieval + _empty_query_result helpers
# ---------------------------------------------------------------------------


def test_empty_query_result_format() -> None:
    """Empty result has unified shape: query/count/results."""
    strategy = RagflowRAGStrategy()
    result = strategy._empty_query_result("hello")
    assert result == {"query": "hello", "count": 0, "results": []}


@pytest.mark.asyncio
async def test_execute_retrieval_returns_converted_results() -> None:
    """_execute_retrieval calls retrieval API + converts response."""
    strategy = RagflowRAGStrategy()
    fake_resp = {
        "code": 0,
        "data": {"chunks": [{"similarity": 0.9, "document_id": "d1", "content": "c1"}]},
    }
    converted = [{"docId": "d1", "content": "c1", "score": 0.9}]
    with patch(
        _RETRIEVAL, new=AsyncMock(return_value=fake_resp)
    ) as mock_retrieval, patch(_CONVERT, return_value=converted):
        result = await strategy._execute_retrieval(
            payload={
                "question": "x",
                "dataset_ids": ["ds-1"],
                "top_k": 5,
                "similarity_threshold": 0.0,
                "vector_similarity_weight": 0.2,
            },
            query="x",
            threshold=0.0,
            effective_top_k=5,
        )
    assert result["count"] == 1
    assert result["query"] == "x"
    assert result["results"][0]["docId"] == "d1"
    # The retrieval client takes request_data via keyword.
    assert mock_retrieval.await_args.kwargs["request_data"]["dataset_ids"] == ["ds-1"]


# ---------------------------------------------------------------------------
# query() integration
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_query_single_repo_routes_to_repo_dataset() -> None:
    """Single repo_ids runs full query pipeline and returns hits.

    Discriminates repo-routing vs default-routing: default name maps to a
    different dataset id, so a query() that ignores repo_ids would fail
    this assertion.
    """
    strategy = RagflowRAGStrategy()
    fake_resp = {
        "code": 0,
        "data": {
            "chunks": [{"similarity": 0.9, "document_id": "d1", "content": "hit"}]
        },
    }
    converted = [{"docId": "d1", "content": "hit", "score": 0.9}]

    async def _resolve(name: str) -> str:
        # Distinct ids per name — proves we routed via repo_ids, not default.
        return "ds-abc" if name == "abc-uuid" else "ds-DEFAULT"

    with patch(_GET_DATASET_NAME, return_value="default-group"), patch(
        _GET_DATASET_ID, new=AsyncMock(side_effect=_resolve)
    ), patch(
        _RETRIEVAL, new=AsyncMock(return_value=fake_resp)
    ) as mock_retrieval, patch(
        _CONVERT, return_value=converted
    ):
        result = await strategy.query(
            query="hello",
            repo_ids=["abc-uuid"],
            doc_ids=None,
            top_k=5,
        )
        sent_payload = mock_retrieval.await_args.kwargs["request_data"]
        # Routes to the repo's dataset, not the default one.
        assert sent_payload["dataset_ids"] == ["ds-abc"]
        assert result["count"] == 1
        assert result["results"][0]["docId"] == "d1"


@pytest.mark.asyncio
async def test_query_all_missing_datasets_returns_empty(caplog) -> None:
    """All repo datasets missing returns empty + warning, no RAGFlow call."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_ID, new=AsyncMock(return_value=None)), patch(
        _RETRIEVAL, new=AsyncMock()
    ) as mock_retrieval:
        with caplog.at_level(
            logging.WARNING, logger="knowledge.service.impl.ragflow_strategy"
        ):
            result = await strategy.query(
                query="hello",
                repo_ids=["xxx", "yyy"],
                doc_ids=None,
                top_k=5,
            )
        mock_retrieval.assert_not_called()
        assert result == {"query": "hello", "count": 0, "results": []}
        assert any("missing" in r.message.lower() for r in caplog.records)


@pytest.mark.asyncio
async def test_query_repo_and_doc_ids_double_filter() -> None:
    """repo_ids + doc_ids: payload has both repo dataset and docs."""
    strategy = RagflowRAGStrategy()
    fake_resp = {"code": 0, "data": {"chunks": []}}

    async def _resolve(name: str) -> str:
        return "ds-abc" if name == "abc-uuid" else "ds-DEFAULT"

    with patch(_GET_DATASET_NAME, return_value="default-group"), patch(
        _GET_DATASET_ID, new=AsyncMock(side_effect=_resolve)
    ), patch(
        _RETRIEVAL, new=AsyncMock(return_value=fake_resp)
    ) as mock_retrieval, patch(
        _CONVERT, return_value=[]
    ):
        await strategy.query(
            query="hello",
            repo_ids=["abc-uuid"],
            doc_ids=["doc1", "doc2"],
            top_k=5,
        )
        sent_payload = mock_retrieval.await_args.kwargs["request_data"]
        assert sent_payload["dataset_ids"] == ["ds-abc"]
        assert sent_payload["document_ids"] == ["doc1", "doc2"]


@pytest.mark.asyncio
async def test_query_propagates_ragflow_errors() -> None:
    """RAGFlow exception bubbles as ThirdPartyException, NOT empty."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_ID, new=AsyncMock(return_value="ds-abc")), patch(
        _RETRIEVAL, new=AsyncMock(side_effect=RuntimeError("RAGFlow 5xx"))
    ):
        with pytest.raises(ThirdPartyException):
            await strategy.query(
                query="hello",
                repo_ids=["abc-uuid"],
                doc_ids=None,
                top_k=5,
            )


# ---------------------------------------------------------------------------
# query_doc / query_doc_name (read paths) — group routing
# ---------------------------------------------------------------------------

_LIST_CHUNKS = (
    "knowledge.service.impl.ragflow_strategy.ragflow_client.list_document_chunks"
)
_GET_DOC_INFO = (
    "knowledge.service.impl.ragflow_strategy.ragflow_client.get_document_info"
)


@pytest.mark.asyncio
async def test_query_doc_routes_to_group_dataset() -> None:
    """query_doc with explicit group resolves dataset by group, not default."""
    strategy = RagflowRAGStrategy()
    with patch(
        _GET_DATASET_ID, new=AsyncMock(return_value="ds-abc")
    ) as mock_lookup, patch(
        _LIST_CHUNKS,
        new=AsyncMock(return_value={"code": 0, "data": {"total": 0, "chunks": []}}),
    ):
        await strategy.query_doc(docId="doc-1", group="abc-uuid")
        mock_lookup.assert_awaited_once_with("abc-uuid")


@pytest.mark.asyncio
async def test_query_doc_with_null_group_falls_back_to_default() -> None:
    """query_doc with group=None falls back to default group."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_NAME, return_value="default-group"), patch(
        _GET_DATASET_ID, new=AsyncMock(return_value="ds-default")
    ) as mock_lookup, patch(
        _LIST_CHUNKS,
        new=AsyncMock(return_value={"code": 0, "data": {"total": 0, "chunks": []}}),
    ):
        await strategy.query_doc(docId="doc-1", group=None)
        mock_lookup.assert_awaited_once_with("default-group")


@pytest.mark.asyncio
async def test_query_doc_returns_empty_when_dataset_missing() -> None:
    """query_doc returns [] when dataset for group not found."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_ID, new=AsyncMock(return_value=None)), patch(
        _LIST_CHUNKS, new=AsyncMock()
    ) as mock_list:
        result = await strategy.query_doc(docId="doc-1", group="ghost-uuid")
        assert result == []
        mock_list.assert_not_called()


@pytest.mark.asyncio
async def test_query_doc_name_routes_to_group_dataset() -> None:
    """query_doc_name with explicit group resolves dataset by group."""
    strategy = RagflowRAGStrategy()
    with patch(
        _GET_DATASET_ID, new=AsyncMock(return_value="ds-abc")
    ) as mock_lookup, patch(
        _GET_DOC_INFO, new=AsyncMock(return_value={"name": "f.pdf", "run": "DONE"})
    ):
        await strategy.query_doc_name(docId="doc-1", group="abc-uuid")
        mock_lookup.assert_awaited_once_with("abc-uuid")


@pytest.mark.asyncio
async def test_query_doc_name_with_null_group_falls_back_to_default() -> None:
    """query_doc_name with group=None falls back to default group."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_NAME, return_value="default-group"), patch(
        _GET_DATASET_ID, new=AsyncMock(return_value="ds-default")
    ) as mock_lookup, patch(
        _GET_DOC_INFO, new=AsyncMock(return_value={"name": "f.pdf", "run": "DONE"})
    ):
        await strategy.query_doc_name(docId="doc-1", group=None)
        mock_lookup.assert_awaited_once_with("default-group")


@pytest.mark.asyncio
async def test_query_doc_name_returns_none_when_dataset_missing() -> None:
    """query_doc_name returns None when dataset for group not found."""
    strategy = RagflowRAGStrategy()
    with patch(_GET_DATASET_ID, new=AsyncMock(return_value=None)), patch(
        _GET_DOC_INFO, new=AsyncMock()
    ) as mock_info:
        result = await strategy.query_doc_name(docId="doc-1", group="ghost-uuid")
        assert result is None
        mock_info.assert_not_called()
