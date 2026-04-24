"""Test SkillPlugin and SkillPluginFactory"""

from dataclasses import dataclass
from typing import Any
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from common.otlp import sid as sid_module
from common.otlp.trace.span import Span

from agent.service.plugin.skill import SkillPlugin, SkillPluginFactory


@dataclass
class _DummySidGen:
    """Simple sid generator for testing environment."""

    value: str = "test-sid"

    def gen(self) -> str:  # pragma: no cover - only for testing environment
        return self.value


@pytest.fixture(autouse=True)
def _setup_test_environment() -> None:
    """Automatically inject environment fixes for all tests."""
    if sid_module.sid_generator2 is None:
        sid_module.sid_generator2 = _DummySidGen()  # type: ignore[assignment]


class TestSkillPluginFactory:
    """Test SkillPluginFactory class"""

    @pytest.fixture
    def factory(self) -> SkillPluginFactory:
        """Create Factory instance for testing"""
        return SkillPluginFactory(
            skills=[
                {
                    "skill_id": "skill-1",
                    "name": "ui-ux-pro-max",
                    "description": "Design reference skill",
                    "download_url": "https://example.com/skill.md",
                    "resources": [
                        {
                            "path": "references/beijing.md",
                            "name": "beijing.md",
                            "download_url": "https://example.com/references/beijing.md",
                            "file_ext": "md",
                            "file_size": 128,
                        }
                    ],
                }
            ]
        )

    def test_gen(self, factory: SkillPluginFactory) -> None:
        """Test generating SkillPlugin"""
        plugins = factory.gen()

        assert len(plugins) == 1
        assert isinstance(plugins[0], SkillPlugin)
        assert plugins[0].name == "read_skill_skill-1"
        assert plugins[0].typ == "skill"

    def test_gen_skips_invalid_skills(self) -> None:
        """Test skipping invalid skill definitions"""
        factory = SkillPluginFactory(
            skills=[
                {
                    "skill_id": "skill-1",
                    "name": "missing-download-url",
                },
                {
                    "name": "missing-id",
                    "download_url": "https://example.com/skill.md",
                },
            ]
        )

        assert factory.gen() == []

    @pytest.mark.asyncio
    async def test_runner_reads_skill_content(
        self, factory: SkillPluginFactory
    ) -> None:
        """Test downloading full skill content on demand"""
        plugin = factory.gen()[0]
        span = Span(app_id="test_app", uid="test_uid")

        def mock_get(*args: Any, **kwargs: Any) -> AsyncMock:  # noqa: ANN001
            mock_resp = AsyncMock()
            mock_resp.raise_for_status = MagicMock()
            mock_resp.text = AsyncMock(return_value="# Skill\n\nFull content")
            mock_resp.__aenter__.return_value = mock_resp
            mock_resp.__aexit__.return_value = False
            return mock_resp

        with patch("aiohttp.ClientSession.get", new=mock_get):
            response = await plugin.run({}, span)

        assert response.result["skill_id"] == "skill-1"
        assert response.result["name"] == "ui-ux-pro-max"
        assert response.result["description"] == "Design reference skill"
        assert response.result["content"] == "# Skill\n\nFull content"
        assert response.result["resources"] == [
            {
                "path": "references/beijing.md",
                "name": "beijing.md",
                "file_ext": "md",
                "file_size": 128,
            }
        ]

    @pytest.mark.asyncio
    async def test_runner_reads_skill_resource_by_path(
        self, factory: SkillPluginFactory
    ) -> None:
        """Test downloading referenced skill resource on demand"""
        plugin = factory.gen()[0]
        span = Span(app_id="test_app", uid="test_uid")

        def mock_get(*args: Any, **kwargs: Any) -> AsyncMock:  # noqa: ANN001
            mock_resp = AsyncMock()
            mock_resp.raise_for_status = MagicMock()
            url = str(args[1]) if len(args) > 1 else ""
            mock_resp.text = AsyncMock(
                return_value="北京参考内容"
                if "beijing.md" in url
                else "# Skill\n\nFull content"
            )
            mock_resp.__aenter__.return_value = mock_resp
            mock_resp.__aexit__.return_value = False
            return mock_resp

        with patch("aiohttp.ClientSession.get", new=mock_get):
            response = await plugin.run({"path": "references/beijing.md"}, span)

        assert response.result["skill_id"] == "skill-1"
        assert response.result["path"] == "references/beijing.md"
        assert response.result["content"] == "北京参考内容"
