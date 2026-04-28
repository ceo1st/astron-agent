#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Tests for RagflowRAGStrategy chunks_save / chunks_update / chunks_delete
group routing behavior."""

import logging
from unittest.mock import AsyncMock, patch

import pytest

from knowledge.exceptions.exception import CustomException
from knowledge.service.impl.ragflow_strategy import RagflowRAGStrategy

_ENSURE_DATASET = "knowledge.service.impl.ragflow_strategy.RagflowUtils.ensure_dataset"
_GET_DATASET_NAME = (
    "knowledge.service.impl.ragflow_strategy.RagflowUtils.get_default_dataset_name"
)
_GET_DATASET_ID = (
    "knowledge.service.impl.ragflow_strategy.RagflowUtils.get_dataset_id_by_name"
)


# ----------------------------------------------------------------------
# chunks_save (lazy create via ensure_dataset)
# ----------------------------------------------------------------------


@pytest.mark.asyncio
async def test_chunks_save_routes_to_group_dataset_with_lazy_create() -> None:
    """chunks_save with explicit group calls ensure_dataset(group)."""
    strategy = RagflowRAGStrategy()
    with patch(
        _ENSURE_DATASET,
        new=AsyncMock(return_value="ds-abc"),
    ) as mock_ensure, patch.object(
        strategy,
        "_validate_document_exists",
        new=AsyncMock(return_value=None),
    ), patch.object(
        strategy,
        "_get_existing_chunks",
        new=AsyncMock(return_value={}),
    ), patch.object(
        strategy,
        "_process_chunks_batch",
        new=AsyncMock(return_value=([{"id": "c1"}], [])),
    ), patch.object(
        strategy,
        "_handle_chunk_results",
        new=AsyncMock(return_value=[{"id": "c1"}]),
    ):
        await strategy.chunks_save(
            docId="doc-1",
            group="abc-uuid",
            uid="user-1",
            chunks=[{"content": "hello"}],
        )
        mock_ensure.assert_awaited_once_with("abc-uuid", description=None)


@pytest.mark.asyncio
async def test_chunks_save_with_null_group_falls_back_to_default() -> None:
    """chunks_save with group=None falls back to RAGFLOW_DEFAULT_GROUP."""
    strategy = RagflowRAGStrategy()
    with patch(
        _GET_DATASET_NAME,
        return_value="default-group",
    ), patch(
        _ENSURE_DATASET,
        new=AsyncMock(return_value="ds-default"),
    ) as mock_ensure, patch.object(
        strategy,
        "_validate_document_exists",
        new=AsyncMock(return_value=None),
    ), patch.object(
        strategy,
        "_get_existing_chunks",
        new=AsyncMock(return_value={}),
    ), patch.object(
        strategy,
        "_process_chunks_batch",
        new=AsyncMock(return_value=([{"id": "c1"}], [])),
    ), patch.object(
        strategy,
        "_handle_chunk_results",
        new=AsyncMock(return_value=[{"id": "c1"}]),
    ):
        await strategy.chunks_save(
            docId="doc-1",
            group=None,
            uid="user-1",
            chunks=[{"content": "hello"}],
        )
        mock_ensure.assert_awaited_once_with("default-group", description=None)


@pytest.mark.asyncio
async def test_chunks_save_lazy_creates_when_group_dataset_missing() -> None:
    """chunks_save uses ensure_dataset which lazy creates if missing."""
    strategy = RagflowRAGStrategy()
    with patch(
        _ENSURE_DATASET,
        new=AsyncMock(return_value="ds-newly-created"),
    ) as mock_ensure, patch.object(
        strategy,
        "_validate_document_exists",
        new=AsyncMock(return_value=None),
    ), patch.object(
        strategy,
        "_get_existing_chunks",
        new=AsyncMock(return_value={}),
    ), patch.object(
        strategy,
        "_process_chunks_batch",
        new=AsyncMock(return_value=([{"id": "c1"}], [])),
    ), patch.object(
        strategy,
        "_handle_chunk_results",
        new=AsyncMock(return_value=[{"id": "c1"}]),
    ):
        await strategy.chunks_save(
            docId="doc-1",
            group="brand-new-repo",
            uid="user-1",
            chunks=[{"content": "hello"}],
        )
        mock_ensure.assert_awaited_once_with("brand-new-repo", description=None)


# ----------------------------------------------------------------------
# chunks_update (no-create, raise on missing)
# ----------------------------------------------------------------------


@pytest.mark.asyncio
async def test_chunks_update_routes_to_group_dataset() -> None:
    """chunks_update with explicit group resolves dataset by group."""
    strategy = RagflowRAGStrategy()
    with patch(
        _GET_DATASET_ID,
        new=AsyncMock(return_value="ds-abc"),
    ) as mock_lookup, patch(
        "knowledge.service.impl.ragflow_strategy.ragflow_client.update_chunk",
        new=AsyncMock(return_value={"code": 0}),
    ):
        await strategy.chunks_update(
            docId="doc-1",
            group="abc-uuid",
            uid="user-1",
            chunks=[{"chunkId": "c1", "content": "new"}],
        )
        mock_lookup.assert_awaited_once_with("abc-uuid")


@pytest.mark.asyncio
async def test_chunks_update_with_null_group_falls_back_to_default() -> None:
    """chunks_update with group=None falls back to default."""
    strategy = RagflowRAGStrategy()
    with patch(
        _GET_DATASET_NAME,
        return_value="default-group",
    ), patch(
        _GET_DATASET_ID,
        new=AsyncMock(return_value="ds-default"),
    ) as mock_lookup, patch(
        "knowledge.service.impl.ragflow_strategy.ragflow_client.update_chunk",
        new=AsyncMock(return_value={"code": 0}),
    ):
        await strategy.chunks_update(
            docId="doc-1",
            group=None,
            uid="user-1",
            chunks=[{"chunkId": "c1", "content": "new"}],
        )
        mock_lookup.assert_awaited_once_with("default-group")


@pytest.mark.asyncio
async def test_chunks_update_raises_when_dataset_missing() -> None:
    """chunks_update raises when dataset not found in RAGFlow."""
    strategy = RagflowRAGStrategy()
    with patch(
        _GET_DATASET_ID,
        new=AsyncMock(return_value=None),
    ):
        with pytest.raises(CustomException) as exc_info:
            await strategy.chunks_update(
                docId="doc-1",
                group="ghost-uuid",
                uid="user-1",
                chunks=[{"chunkId": "c1", "content": "x"}],
            )
        # Strong assertion: error message must mention all three signals
        msg = str(exc_info.value).lower()
        assert "not found" in msg
        assert "update" in msg
        assert "ghost-uuid" in msg


# ----------------------------------------------------------------------
# chunks_delete (signature add group + silent skip)
# ----------------------------------------------------------------------


@pytest.mark.asyncio
async def test_chunks_delete_routes_to_group_dataset() -> None:
    """chunks_delete with explicit group resolves dataset by group."""
    strategy = RagflowRAGStrategy()
    with patch(
        _GET_DATASET_ID,
        new=AsyncMock(return_value="ds-abc"),
    ) as mock_lookup, patch(
        "knowledge.service.impl.ragflow_strategy.ragflow_client.delete_chunks",
        new=AsyncMock(return_value={"code": 0}),
    ):
        await strategy.chunks_delete(
            docId="doc-1",
            chunkIds=["c1", "c2"],
            group="abc-uuid",
        )
        mock_lookup.assert_awaited_once_with("abc-uuid")


@pytest.mark.asyncio
async def test_chunks_delete_with_null_group_falls_back_to_default() -> None:
    """chunks_delete with group=None falls back to default."""
    strategy = RagflowRAGStrategy()
    with patch(
        _GET_DATASET_NAME,
        return_value="default-group",
    ), patch(
        _GET_DATASET_ID,
        new=AsyncMock(return_value="ds-default"),
    ) as mock_lookup, patch(
        "knowledge.service.impl.ragflow_strategy.ragflow_client.delete_chunks",
        new=AsyncMock(return_value={"code": 0}),
    ):
        await strategy.chunks_delete(
            docId="doc-1",
            chunkIds=["c1"],
            group=None,
        )
        mock_lookup.assert_awaited_once_with("default-group")


@pytest.mark.asyncio
async def test_chunks_delete_silent_skip_when_dataset_missing(
    caplog: pytest.LogCaptureFixture,
) -> None:
    """chunks_delete returns no-op + logs info when dataset missing."""
    strategy = RagflowRAGStrategy()
    with patch(
        _GET_DATASET_ID,
        new=AsyncMock(return_value=None),
    ), patch(
        "knowledge.service.impl.ragflow_strategy.ragflow_client.delete_chunks",
        new=AsyncMock(),
    ) as mock_delete:
        with caplog.at_level(
            logging.INFO,
            logger="knowledge.service.impl.ragflow_strategy",
        ):
            await strategy.chunks_delete(
                docId="doc-1",
                chunkIds=["c1"],
                group="ghost-uuid",
            )
        mock_delete.assert_not_called()
        assert any(
            "no-op" in r.message.lower() for r in caplog.records
        ), f"Expected 'no-op' in logs, got: {[r.message for r in caplog.records]}"
