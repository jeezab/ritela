---
name: ritela-resume
description: Resume work in the Ritela repository from its saved checkpoint and inspect toolchain availability. Use at a new session or when asked to continue this project.
---

# Resume Ritela

1. Read `docs/STATUS.md` and `docs/PROJECT_MAP.md` from the repository root.
2. Run `powershell -NoProfile -File scripts/resume.ps1`. It reads Git and checks available tools without installing anything.
3. Compare the recorded next action with actual files and dirty changes. Preserve user changes; do not rerun completed bootstrap or reread the full brief without a concrete need.
4. Read only the relevant files and sections of `docs/PROJECT_BRIEF.md`. Follow root AGENTS and CONTRIBUTING.
5. Perform the recorded next action, then update STATUS with observed checks and the next step. A documentation check is not proof of an Android build.

Paths are relative to the repository root, not this skill directory. Do not put credentials, health data or large command logs in the checkpoint.
