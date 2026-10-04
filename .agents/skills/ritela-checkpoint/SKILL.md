---
name: ritela-checkpoint
description: Save a completed Ritela work step in current documentation and a verified Git commit. Use at a milestone boundary, before ending a session, or when asked to preserve progress.
---

# Save a checkpoint

1. Inspect `git status --short` and the relevant diff. Separate this step from unrelated existing edits.
2. Update `docs/STATUS.md` with completed behavior, exact checks/results, limitations and one actionable next step. Update PROJECT_MAP for structure changes and DECISIONS for durable decisions. Keep the original prompts intact.
3. Run `powershell -NoProfile -File scripts/check-workspace.ps1`, `git diff --check` and the checks appropriate to the changed code. Record failures honestly; do not claim a milestone complete while required Android checks are unavailable or failing.
4. Stage explicit paths only. Review staged contents and run `git diff --cached --check`.
5. Commit the finished step with the format defined in `CONTRIBUTING.md`. This user has requested routine local commits. Do not amend existing history or push as part of this procedure.
6. Check the last commit and working tree; report the commit identifier and any remaining edits.

Git supplies the current commit to the resume script. Avoid a self-referential SHA in STATUS. Extract a new script/skill only when a real repeated procedure justifies it; update the skill registry and hashes when editing local skills.
