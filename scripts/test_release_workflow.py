#!/usr/bin/env python3
from pathlib import Path


workflow = (
    Path(__file__).resolve().parents[1] / ".github" / "workflows" / "android.yml"
).read_text()

assert 'tag="v${APP_VERSION}-build-${GITHUB_RUN_NUMBER}"' in workflow
assert "fetch-depth: 0" in workflow
assert (
    "git log --no-merges --reverse --format='- %s (`%h`)'"
    in workflow
)
assert "--generate-notes" in workflow
assert "--notes-file" not in workflow
