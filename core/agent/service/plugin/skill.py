import json
from typing import Any

import aiohttp
from common.otlp.trace.span import Span
from openai import BaseModel

from agent.service.plugin.base import BasePlugin, PluginResponse


class SkillPlugin(BasePlugin):
    skill_id: str
    download_url: str


class SkillPluginFactory(BaseModel):
    skills: list[dict[str, Any]]

    def gen(self) -> list[SkillPlugin]:
        plugins: list[SkillPlugin] = []
        for skill in self.skills:
            skill_id = str(skill.get("skill_id") or skill.get("skillId") or "")
            name = str(skill.get("name") or "").strip()
            description = str(skill.get("description") or "").strip()
            download_url = str(skill.get("download_url") or skill.get("downloadUrl") or "").strip()
            if not (skill_id and name and download_url):
                continue
            plugins.append(
                SkillPlugin(
                    skill_id=skill_id,
                    name=f"read_skill_{skill_id}",
                    description=description or f"Read the full SKILL.md for {name}",
                    schema_template=(
                        f"tool_name:read_skill_{skill_id}, "
                        f"tool_description:Read the complete SKILL.md content for skill '{name}'. "
                        f"Use it only when detailed procedures are required. "
                        'tool_parameters:{"type":"object","properties":{},"required":[]}'
                    ),
                    typ="skill",
                    download_url=download_url,
                    run=self._build_runner(skill_id, name, description, download_url),
                )
            )
        return plugins

    def _build_runner(
        self,
        skill_id: str,
        name: str,
        description: str,
        download_url: str,
    ):
        async def _runner(action_input: dict[str, Any], span: Span) -> PluginResponse:
            with span.start(f"ReadSkill-{skill_id}") as sp:
                sp.add_info_events(
                    {
                        "skill_id": skill_id,
                        "skill_name": name,
                        "download_url": download_url,
                        "action_input": json.dumps(action_input, ensure_ascii=False),
                    }
                )
                timeout = aiohttp.ClientTimeout(total=30)
                async with aiohttp.ClientSession(timeout=timeout) as session:
                    async with session.get(download_url) as response:
                        response.raise_for_status()
                        content = await response.text()
                result = {
                    "skill_id": skill_id,
                    "name": name,
                    "description": description,
                    "content": content,
                }
                return PluginResponse(result=result)

        return _runner
