import json
from typing import Any

import aiohttp
from common.otlp.trace.span import Span
from openai import BaseModel

from agent.service.plugin.base import BasePlugin, PluginResponse


class SkillResource(BaseModel):
    path: str
    name: str = ""
    download_url: str = ""
    file_ext: str = ""
    file_size: int = 0


class SkillPlugin(BasePlugin):
    skill_id: str
    download_url: str
    resources: list[SkillResource] = []


class SkillPluginFactory(BaseModel):
    skills: list[dict[str, Any]]

    def gen(self) -> list[SkillPlugin]:
        plugins: list[SkillPlugin] = []
        for skill in self.skills:
            skill_id = str(skill.get("skill_id") or skill.get("skillId") or "")
            name = str(skill.get("name") or "").strip()
            description = str(skill.get("description") or "").strip()
            download_url = str(skill.get("download_url") or skill.get("downloadUrl") or "").strip()
            resources = self._normalize_resources(skill.get("resources") or [])
            if not (skill_id and name and download_url):
                continue
            plugins.append(
                SkillPlugin(
                    skill_id=skill_id,
                    name=f"read_skill_{skill_id}",
                    description=description or f"Read the full skill package for {name}",
                    schema_template=(
                        f"tool_name:read_skill_{skill_id}, "
                        f"tool_description:Read SKILL.md and referenced files for skill '{name}'. "
                        f"First call with empty parameters to read SKILL.md and get the resource manifest. "
                        f"If SKILL.md references a relative path like references/beijing.md, call again with that path. "
                        'tool_parameters:{"type":"object","properties":{"path":{"type":"string","description":"Optional relative resource path under the skill folder, for example references/beijing.md. Leave empty to read SKILL.md and list available resources."}},"required":[]}'
                    ),
                    typ="skill",
                    download_url=download_url,
                    resources=resources,
                    run=self._build_runner(
                        skill_id, name, description, download_url, resources
                    ),
                )
            )
        return plugins

    def _normalize_resources(self, raw_resources: Any) -> list[SkillResource]:
        resources: list[SkillResource] = []
        if not isinstance(raw_resources, list):
            return resources
        for item in raw_resources:
            if not isinstance(item, dict):
                continue
            path = self._normalize_path(item.get("path"))
            download_url = str(
                item.get("download_url") or item.get("downloadUrl") or ""
            ).strip()
            if not (path and download_url):
                continue
            resources.append(
                SkillResource(
                    path=path,
                    name=str(item.get("name") or "").strip(),
                    download_url=download_url,
                    file_ext=str(item.get("file_ext") or item.get("fileExt") or "").strip(),
                    file_size=int(item.get("file_size") or item.get("fileSize") or 0),
                )
            )
        return resources

    def _build_runner(
        self,
        skill_id: str,
        name: str,
        description: str,
        download_url: str,
        resources: list[SkillResource],
    ):
        async def _runner(action_input: dict[str, Any], span: Span) -> PluginResponse:
            with span.start(f"ReadSkill-{skill_id}") as sp:
                requested_path = self._normalize_path(action_input.get("path"))
                sp.add_info_events(
                    {
                        "skill_id": skill_id,
                        "skill_name": name,
                        "download_url": download_url,
                        "requested_path": requested_path,
                        "action_input": json.dumps(action_input, ensure_ascii=False),
                    }
                )
                if requested_path:
                    resource = next(
                        (
                            item
                            for item in resources
                            if self._normalize_path(item.path) == requested_path
                        ),
                        None,
                    )
                    if resource is None:
                        return PluginResponse(
                            result={
                                "skill_id": skill_id,
                                "name": name,
                                "description": description,
                                "path": requested_path,
                                "error": "resource_not_found",
                                "available_resources": [
                                    {
                                        "path": item.path,
                                        "name": item.name,
                                        "file_ext": item.file_ext,
                                        "file_size": item.file_size,
                                    }
                                    for item in resources
                                ],
                            }
                        )
                    content = await self._download_text(resource.download_url)
                    return PluginResponse(
                        result={
                            "skill_id": skill_id,
                            "name": name,
                            "description": description,
                            "path": resource.path,
                            "content": content,
                        }
                    )

                content = await self._download_text(download_url)
                result = {
                    "skill_id": skill_id,
                    "name": name,
                    "description": description,
                    "content": content,
                    "resources": [
                        {
                            "path": item.path,
                            "name": item.name,
                            "file_ext": item.file_ext,
                            "file_size": item.file_size,
                        }
                        for item in resources
                    ],
                }
                return PluginResponse(result=result)

        return _runner

    def _normalize_path(self, value: Any) -> str:
        path = str(value or "").strip().replace("\\", "/")
        while path.startswith("./"):
            path = path[2:]
        return path.lstrip("/")

    async def _download_text(self, url: str) -> str:
        timeout = aiohttp.ClientTimeout(total=30)
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.get(url) as response:
                response.raise_for_status()
                return await response.text()
