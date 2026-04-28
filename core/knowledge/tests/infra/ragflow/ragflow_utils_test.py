#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Unit tests for ``RagflowUtils`` helpers used by the RAGFlow strategy.

Covers:

- ``get_default_dataset_name``: unified ``RAGFLOW_DEFAULT_GROUP`` reader
  across the four strategy call sites (``split`` / ``chunks_save`` /
  ``chunks_update`` / ``chunks_delete``).
- ``get_dataset_id_by_name``: dataset-lookup failures should propagate
  instead of collapsing into ``None``.
"""

from unittest.mock import AsyncMock, patch

import aiohttp
import pytest

from knowledge.exceptions.exception import ThirdPartyException
from knowledge.infra.ragflow.ragflow_utils import (
    DEFAULT_RAGFLOW_DATASET_NAME,
    RagflowUtils,
)

# Mock the module-level ``list_datasets`` import resolved inside
# ``get_dataset_id_by_name``. The helper reaches it via
# ``ragflow_client.list_datasets``; patching the client attribute covers
# both dotted and direct-import access paths.
_LIST_DATASETS = "knowledge.infra.ragflow.ragflow_client.list_datasets"


class TestGetDefaultDatasetName:
    """Tests for ``RagflowUtils.get_default_dataset_name``."""

    def test_returns_env_value_when_set(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("RAGFLOW_DEFAULT_GROUP", "MyCustomKB")
        assert RagflowUtils.get_default_dataset_name() == "MyCustomKB"

    def test_returns_default_when_env_unset(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.delenv("RAGFLOW_DEFAULT_GROUP", raising=False)
        assert RagflowUtils.get_default_dataset_name() == DEFAULT_RAGFLOW_DATASET_NAME

    def test_returns_default_when_env_empty(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.setenv("RAGFLOW_DEFAULT_GROUP", "")
        assert RagflowUtils.get_default_dataset_name() == DEFAULT_RAGFLOW_DATASET_NAME


class TestGetDatasetIdByName:
    """Tests for ``RagflowUtils.get_dataset_id_by_name``."""

    @pytest.mark.asyncio
    async def test_returns_id_on_matching_dataset(self) -> None:
        """code=0 + matching dataset => returns dataset id."""
        resp = {"code": 0, "data": [{"id": "ds-1", "name": "MyKB"}]}
        with patch(_LIST_DATASETS, new=AsyncMock(return_value=resp)):
            result = await RagflowUtils.get_dataset_id_by_name("MyKB")
        assert result == "ds-1"

    @pytest.mark.asyncio
    async def test_returns_none_when_no_match(self) -> None:
        """Back-compat: code=0 + empty data => ``None`` so the caller can
        treat it as 'dataset not configured', preserving the
        fallback-to-empty-result behavior for genuine no-match cases."""
        resp = {"code": 0, "data": []}
        with patch(_LIST_DATASETS, new=AsyncMock(return_value=resp)):
            result = await RagflowUtils.get_dataset_id_by_name("MissingKB")
        assert result is None

    @pytest.mark.asyncio
    async def test_raises_on_transport_exception(self) -> None:
        """aiohttp errors propagate instead of being collapsed into ``None``."""
        with patch(
            _LIST_DATASETS,
            new=AsyncMock(side_effect=aiohttp.ClientConnectionError("down")),
        ):
            with pytest.raises(aiohttp.ClientConnectionError):
                await RagflowUtils.get_dataset_id_by_name("AnyKB")

    @pytest.mark.asyncio
    async def test_raises_on_non_zero_code(self) -> None:
        """Non-zero code (auth / argument / operating error) => ThirdPartyException.
        Genuine protocol failures still surface; only the not-found shapes
        below are folded into ``None``."""
        resp = {"code": 109, "message": "Authentication failed"}
        with patch(_LIST_DATASETS, new=AsyncMock(return_value=resp)):
            with pytest.raises(ThirdPartyException) as exc_info:
                await RagflowUtils.get_dataset_id_by_name("AnyKB")
        assert "Authentication failed" in str(exc_info.value)

    @pytest.mark.asyncio
    async def test_returns_none_on_code_108_lacks_permission(self) -> None:
        """code=108 with 'lacks permission' message => not-found => ``None``.

        RAGFlow returns this shape for both nonexistent and inaccessible
        datasets; callers cannot tell them apart, so the safe fail-closed
        behavior is to treat it as not-found rather than raising.
        """
        resp = {
            "code": 108,
            "message": "User 'u-1' lacks permission for dataset 'MissingKB'",
        }
        with patch(_LIST_DATASETS, new=AsyncMock(return_value=resp)):
            result = await RagflowUtils.get_dataset_id_by_name("MissingKB")
        assert result is None

    @pytest.mark.asyncio
    async def test_raises_on_code_108_unrelated_message(self) -> None:
        """code=108 without 'lacks permission' marker still raises so we don't
        silently swallow other 108-class errors."""
        resp = {"code": 108, "message": "Some other 108 error"}
        with patch(_LIST_DATASETS, new=AsyncMock(return_value=resp)):
            with pytest.raises(ThirdPartyException):
                await RagflowUtils.get_dataset_id_by_name("AnyKB")


# ---------------------------------------------------------------------------
# ensure_dataset description sync (lazy + best-effort)
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_ensure_dataset_skips_update_when_description_matches() -> None:
    """No update_dataset call when existing description == desired."""
    list_resp = {
        "code": 0,
        "data": [{"name": "g", "id": "ds-1", "description": "客服库"}],
    }
    with patch(
        "knowledge.infra.ragflow.ragflow_utils.list_datasets",
        new=AsyncMock(return_value=list_resp),
    ), patch(
        "knowledge.infra.ragflow.ragflow_utils.update_dataset", new=AsyncMock()
    ) as mock_update:
        result = await RagflowUtils.ensure_dataset("g", description="客服库")
        assert result == "ds-1"
        mock_update.assert_not_called()


@pytest.mark.asyncio
async def test_ensure_dataset_updates_when_description_stale() -> None:
    """Best-effort update when existing description differs from desired."""
    list_resp = {
        "code": 0,
        "data": [{"name": "g", "id": "ds-1", "description": "Old name"}],
    }
    with patch(
        "knowledge.infra.ragflow.ragflow_utils.list_datasets",
        new=AsyncMock(return_value=list_resp),
    ), patch(
        "knowledge.infra.ragflow.ragflow_utils.update_dataset",
        new=AsyncMock(return_value={"code": 0}),
    ) as mock_update:
        result = await RagflowUtils.ensure_dataset("g", description="新名字")
        assert result == "ds-1"
        mock_update.assert_awaited_once_with("ds-1", description="新名字")


@pytest.mark.asyncio
async def test_ensure_dataset_skips_update_when_description_none() -> None:
    """No update when caller passes description=None (no label to sync)."""
    list_resp = {
        "code": 0,
        "data": [{"name": "g", "id": "ds-1", "description": "Old name"}],
    }
    with patch(
        "knowledge.infra.ragflow.ragflow_utils.list_datasets",
        new=AsyncMock(return_value=list_resp),
    ), patch(
        "knowledge.infra.ragflow.ragflow_utils.update_dataset", new=AsyncMock()
    ) as mock_update:
        await RagflowUtils.ensure_dataset("g", description=None)
        mock_update.assert_not_called()


@pytest.mark.asyncio
async def test_ensure_dataset_swallows_update_failures(caplog) -> None:
    """update_dataset failure logs warning + returns dataset_id (does not raise)."""
    import logging

    list_resp = {
        "code": 0,
        "data": [{"name": "g", "id": "ds-1", "description": "stale"}],
    }
    with patch(
        "knowledge.infra.ragflow.ragflow_utils.list_datasets",
        new=AsyncMock(return_value=list_resp),
    ), patch(
        "knowledge.infra.ragflow.ragflow_utils.update_dataset",
        new=AsyncMock(side_effect=RuntimeError("RAGFlow 5xx")),
    ):
        with caplog.at_level(logging.WARNING):
            result = await RagflowUtils.ensure_dataset("g", description="new")
        assert result == "ds-1"
        assert any(
            "Best-effort description sync failed" in r.message for r in caplog.records
        )


@pytest.mark.asyncio
async def test_ensure_dataset_warns_on_non_zero_update_code(caplog) -> None:
    """update_dataset HTTP 200 with non-zero RAGFlow code logs warning, no raise."""
    import logging

    list_resp = {
        "code": 0,
        "data": [{"name": "g", "id": "ds-1", "description": "stale"}],
    }
    update_resp = {"code": 102, "message": "permission denied"}
    with patch(
        "knowledge.infra.ragflow.ragflow_utils.list_datasets",
        new=AsyncMock(return_value=list_resp),
    ), patch(
        "knowledge.infra.ragflow.ragflow_utils.update_dataset",
        new=AsyncMock(return_value=update_resp),
    ):
        with caplog.at_level(logging.WARNING):
            result = await RagflowUtils.ensure_dataset("g", description="new")
        assert result == "ds-1"
        assert any(
            "rejected by RAGFlow" in r.message and "permission denied" in r.message
            for r in caplog.records
        )
