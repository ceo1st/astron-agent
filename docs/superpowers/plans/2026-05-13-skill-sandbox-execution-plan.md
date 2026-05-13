# Skill Sandbox Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add sandbox-backed `run_skill_xxx` execution for Skill scripts, E2B configuration UI, and workflow artifact file viewing.

**Architecture:** The runtime always exposes `read_skill_xxx` and `run_skill_xxx`; `run_skill_xxx` stages Skill resources and executes the model-provided command through a sandbox provider. Console backend stores sandbox configuration and workflow artifacts, while the frontend adds resource sandbox settings and a workflow files tab.

**Tech Stack:** Python FastAPI runtime, Java Spring Boot/MyBatis Plus backend, React 18/TypeScript/Ant Design frontend, E2B official Python SDK.

---

### Task 1: Runtime Skill Execution Contract

**Files:**
- Modify: `core/agent/service/plugin/skill.py`
- Create: `core/agent/service/plugin/skill_sandbox.py`
- Test: `core/agent/tests/test_skill_plugin.py`

- [ ] **Step 1: Write failing tests**

Add tests that assert `SkillPluginFactory.gen()` returns both `read_skill_xxx` and `run_skill_xxx`, and that `run_skill_xxx` returns the fixed unsupported message when sandbox configuration is missing.

- [ ] **Step 2: Run runtime tests and verify failure**

Run: `python -m pytest tests/test_skill_plugin.py -q` from `core/agent`.

- [ ] **Step 3: Implement minimal runtime code**

Add `SkillSandboxRunner`, `SandboxExecutionRequest`, and `SandboxExecutionResult`. Wire `SkillPluginFactory` to create `run_skill_xxx` by default.

- [ ] **Step 4: Run runtime tests and verify pass**

Run: `python -m pytest tests/test_skill_plugin.py -q` from `core/agent`.

### Task 2: E2B Provider Skeleton

**Files:**
- Modify: `core/agent/pyproject.toml`
- Modify: `core/agent/service/plugin/skill_sandbox.py`
- Test: `core/agent/tests/test_skill_plugin.py`

- [ ] **Step 1: Write failing tests**

Add mocked-provider tests that verify command, stdin, working directory, and output directory are passed to the provider abstraction without host execution.

- [ ] **Step 2: Run tests and verify failure**

Run: `python -m pytest tests/test_skill_plugin.py -q` from `core/agent`.

- [ ] **Step 3: Implement provider abstraction and E2B import path**

Use the official `e2b` SDK import path and keep live E2B calls behind dependency injection for tests.

- [ ] **Step 4: Run tests and verify pass**

Run: `python -m pytest tests/test_skill_plugin.py -q` from `core/agent`.

### Task 3: Sandbox Configuration API

**Files:**
- Create: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/entity/table/skill/SkillSandboxConfig.java`
- Create: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/mapper/skill/SkillSandboxConfigMapper.java`
- Create: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/service/skill/SkillSandboxConfigService.java`
- Create: `console/backend/toolkit/src/main/java/com/iflytek/astron/console/toolkit/controller/skill/SkillSandboxConfigController.java`
- Create: `console/backend/toolkit/src/main/resources/mapper/skill/SkillSandboxConfigMapper.xml`
- Create: migration under `console/backend/hub/src/main/resources/db/migration`
- Test: toolkit controller/service tests if the module test setup supports them.

- [ ] **Step 1: Write failing backend tests where existing test setup allows**

Cover masked API key behavior and absent configuration response.

- [ ] **Step 2: Implement table, service, controller, and migration**

Scope config by `space_id` or user, store masked fields safely, and expose `GET`, `PUT`, and `POST /skill-sandbox/test`.

- [ ] **Step 3: Run backend tests**

Run the focused Maven test for the touched module.

### Task 4: Workflow Artifact API

**Files:**
- Create artifact entity, mapper, service, controller under the workflow/backend module that owns workflow APIs.
- Create mapper XML and migration.
- Add frontend service types later in Task 6.

- [ ] **Step 1: Write failing artifact service tests where supported**

Cover list, download URL generation, soft delete, and workflow permission checks.

- [ ] **Step 2: Implement metadata persistence and APIs**

Add list/download/delete APIs for workflow artifacts.

- [ ] **Step 3: Run backend tests**

Run focused Maven tests for artifact code.

### Task 5: Resource Management Sandbox Settings UI

**Files:**
- Modify: `console/frontend/src/components/header/index.tsx`
- Modify: `console/frontend/src/pages/resource-management/index.tsx`
- Create: `console/frontend/src/pages/resource-management/sandbox-page/index.tsx`
- Create or modify frontend service/type files for sandbox config.

- [ ] **Step 1: Add frontend test or type-check target**

Use the existing frontend verification path if tests are not present.

- [ ] **Step 2: Implement compact settings page**

Follow `ui-ux-pro-max`: operational form layout, stable hover/focus states, no decorative cards.

- [ ] **Step 3: Run frontend type/lint checks**

Run the focused frontend verification command available in the project.

### Task 6: Workflow Files Tab UI

**Files:**
- Modify: `console/frontend/src/router/index.tsx`
- Modify: `console/frontend/src/pages/workflow/components/flow-header/index.tsx`
- Create: `console/frontend/src/pages/workflow/workflow-files/index.tsx`
- Create or modify frontend service/type files for workflow artifacts.

- [ ] **Step 1: Add frontend test or type-check target**

Use the existing frontend verification path if tests are not present.

- [ ] **Step 2: Add `文件` tab beside analysis**

Route to `/work_flow/:id/files`, render artifact table, and support download/delete.

- [ ] **Step 3: Run frontend type/lint checks**

Run the focused frontend verification command available in the project.

### Task 7: Final Verification

**Files:**
- All touched files.

- [ ] **Step 1: Run runtime tests**

Run: `python -m pytest tests/test_skill_plugin.py -q` from `core/agent`.

- [ ] **Step 2: Run backend focused tests**

Run focused Maven tests for Skill config and artifacts.

- [ ] **Step 3: Run frontend verification**

Run frontend type/lint/build check available in `console/frontend`.

- [ ] **Step 4: Review git diff**

Confirm changes match the design and do not include unrelated refactors.

