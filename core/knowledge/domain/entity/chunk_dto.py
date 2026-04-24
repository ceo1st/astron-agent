# -*- coding: utf-8 -*-
"""
Data model definition module
Contains request model definitions for file splitting, chunking operations, and queries
Uses Pydantic for data validation and serialization
"""

from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field, model_validator


class RAGType(str, Enum):
    """Define RAG type enumeration"""

    AIUI_RAG2 = "AIUI-RAG2"
    CBG_RAG = "CBG-RAG"
    SparkDesk_RAG = "SparkDesk-RAG"
    RagFlow_RAG = "Ragflow-RAG"


class FileSplitReq(BaseModel):
    """
    File splitting request model

    Attributes:
        file: File content or path, required
        resourceType: Resource type, 0-regular file, 1-URL webpage, default is 0
        ragType: RAG type
        lengthRange: Split length range, optional
        overlap: Overlap length, optional
        separator: Separator list, optional
        cutOff: Cutoff marker list, optional
        titleSplit: Whether to split by title, default is False
        documentId: Existing RAGFlow doc id for re-slice upsert, optional
    """

    file: str = Field(..., min_length=1, description="Required, minimum length 1")
    resourceType: Optional[int] = Field(
        default=0, description="0-regular file; 1-URL webpage"
    )
    ragType: RAGType = Field(..., description="RAG type")
    lengthRange: Optional[List[int]] = Field(
        default=None, description="Split length range"
    )
    overlap: Optional[int] = Field(default=None, description="Overlap length")
    separator: Optional[List[str]] = Field(default=None, description="Separator list")
    cutOff: Optional[List[str]] = Field(default=None, description="Cutoff marker list")
    titleSplit: Optional[bool] = Field(
        default=False, description="Whether to split by title"
    )
    documentId: Optional[str] = Field(
        default=None,
        description=(
            "Existing RAGFlow doc id, triggers blue-green upsert. "
            "Omit or leave empty for first-time slicing."
        ),
    )


class ChunkSaveReq(BaseModel):
    """
    Chunk save request model

    Attributes:
        docId: Document ID, required
        group: Group identifier, required
        uid: User ID, optional
        chunks: Chunk list, must contain at least one element
        ragType: RAG type
    """

    docId: str = Field(..., min_length=1, description="Required, minimum length 1")
    group: str = Field(..., min_length=1, description="Required, minimum length 1")
    uid: Optional[str] = Field(default=None, description="User ID")
    chunks: List[Any] = Field(
        ..., min_length=1, description="Chunk list, must contain at least one element"
    )
    ragType: RAGType = Field(..., description="RAG type")


class ChunkUpdateReq(BaseModel):
    """
    Chunk update request model

    Attributes:
        docId: Document ID, required
        group: Group identifier, required
        uid: User ID, optional
        chunks: Chunk dictionary list, must contain at least one element
        ragType: RAG type
    """

    docId: str = Field(..., min_length=1, description="Required, minimum length 1")
    group: str = Field(..., min_length=1, description="Required, minimum length 1")
    uid: Optional[str] = Field(default=None, description="User ID")
    chunks: List[dict] = Field(
        ...,
        min_length=1,
        description="Chunk dictionary list, must contain at least one element",
    )
    ragType: RAGType = Field(..., description="RAG type")


class ChunkDeleteReq(BaseModel):
    """
    Chunk delete request model

    Attributes:
        docId: Document ID, required
        chunkIds: Chunk ID list, optional
        ragType: RAG type
    """

    docId: str = Field(..., min_length=1, description="Required, minimum length 1")
    chunkIds: Optional[List[str]] = Field(default=None, description="Chunk ID list")
    ragType: RAGType = Field(..., description="RAG type")


class QueryMatch(BaseModel):
    """
    Query matching condition model

    Attributes:
        docIds: Document ID list, optional
        repoId: Knowledge base ID list, must contain at least one element
        threshold: Similarity threshold, range 0~1, default is 0
        flowId: Flow ID, optional
    """

    docIds: Optional[List[str]] = Field(default=None, description="Document ID list")
    repoId: List[str] = Field(
        ...,
        min_length=1,
        description="Knowledge base ID list, must contain at least one element",
    )
    threshold: float = Field(
        default=0, ge=0, le=1, description="Optional, default value 0, range 0~1"
    )
    flowId: Optional[str] = Field(default=None, description="Flow ID")


class RagflowQueryExt(BaseModel):
    """
    Optional RAGFlow-specific retrieval parameters.

    Fields are mapped to the RAGFlow retrieval request. Only consumed when
    ragType=Ragflow-RAG.
    """

    top_k: Optional[int] = Field(
        default=None,
        ge=1,
        le=200,
        description=(
            "RAGFlow result limit (1~200). When set, replaces topN as the "
            "effective result cap."
        ),
    )
    vector_similarity_weight: Optional[float] = Field(
        default=None,
        ge=0,
        le=1,
        description="Vector-vs-keyword blend weight (0~1).",
    )
    keyword: Optional[bool] = Field(
        default=None,
        description="Enable keyword (BM25) matching.",
    )
    rerank_id: Optional[str] = Field(
        default=None,
        min_length=1,
        description="RAGFlow rerank model identifier.",
    )
    highlight: Optional[bool] = Field(
        default=None,
        description="Return matched-term highlights.",
    )
    use_kg: Optional[bool] = Field(
        default=None,
        description="Consult RAGFlow knowledge graph during retrieval.",
    )


class ChunkQueryReq(BaseModel):
    """
    Chunk query request model

    Attributes:
        query: Query text, required
        topN: Default result limit (1~5); overridden by ragflow_ext.top_k
              when provided.
        rewrite: Whether to rewrite the query before retrieval.
        match: Matching conditions
        ragType: RAG type
        history: Conversation history used during rewrite
        ragflow_ext: Optional RAGFlow-specific retrieval parameters;
                     rejected with other RAG types.
    """

    query: str = Field(..., min_length=1, description="Required, minimum length 1")
    topN: int = Field(..., ge=1, le=5, description="Required, range 1~5")
    rewrite: bool = Field(
        default=True,
        description=(
            "Whether to rewrite the query before retrieval. Set False to "
            "send the raw query (useful for keyword / highlight matching)."
        ),
    )
    match: QueryMatch = Field(..., description="Matching conditions")
    ragType: RAGType = Field(..., description="RAG type")
    history: List[Dict[str, Any]] = Field(default_factory=list)
    ragflow_ext: Optional[RagflowQueryExt] = Field(
        default=None,
        description=(
            "Optional RAGFlow-specific retrieval parameters. Requires "
            "ragType=Ragflow-RAG; other RAG types return a validation "
            "error. When ragflow_ext.top_k is set, it overrides topN."
        ),
    )

    @model_validator(mode="after")
    def _ragflow_ext_scope_check(self) -> "ChunkQueryReq":
        if self.ragflow_ext is not None and self.ragType != RAGType.RagFlow_RAG:
            raise ValueError(
                f"ragflow_ext is only allowed when ragType='Ragflow-RAG', "
                f"got ragType='{self.ragType.value}'"
            )
        return self


class QueryDocReq(BaseModel):
    """
    Document query request model

    Attributes:
        docId: Document ID, required
        ragType: RAG type
    """

    docId: str = Field(..., min_length=1, description="Required, minimum length 1")
    ragType: RAGType = Field(..., description="RAG type")
