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
        RAGFlow's ``/datasets`` endpoint returns ``code=0, data=[]`` for
        genuine no-match, so any non-zero code is a real protocol failure."""
        resp = {"code": 109, "message": "Authentication failed"}
        with patch(_LIST_DATASETS, new=AsyncMock(return_value=resp)):
            with pytest.raises(ThirdPartyException) as exc_info:
                await RagflowUtils.get_dataset_id_by_name("AnyKB")
        assert "Authentication failed" in str(exc_info.value)
