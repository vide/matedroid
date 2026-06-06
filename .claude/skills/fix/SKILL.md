---
name: fix
description: Fix a GitHub issue following the standard workflow. Reads the issue, creates a branch/PR, applies the fix, runs tests, and merges when CI passes.
allowed-tools: Read, Edit, Write, Bash, Glob, Grep, mcp__github__issue_read, mcp__github__add_issue_comment, mcp__github__create_pull_request, mcp__github__pull_request_read, mcp__github__merge_pull_request, mcp__github__update_pull_request, mcp__github__list_issues
---

# Fix GitHub Issue Skill

Fix a GitHub issue following the complete workflow from investigation to merge.

## Arguments

The skill expects a GitHub issue number as argument:
```
/fix 123
```

If no argument is provided, ask the user for the issue number.

## Workflow

### 1. Read the Issue

The repository is called `matedroid` and it is owned by `vide`.

Use `mcp__github__issue_read` to get the issue details:
- Issue title and description
- Any existing comments
- Labels and assignees
- References to other issues, noted by the #123 format.

**Important**: Note the language the issue was written in. You will use this language for all interactions on the issue itself.

Also check whether the issue has a **linked or proposed pull request** (a community/external PR that claims to fix it). If it does, the security gate below is MANDATORY.

### 1a. SECURITY GATE — mandatory for any externally-proposed PR

> ⚠️ If a fix is supplied as a pull request you did not author yourself (community contribution, fork, drive-by PR linked from the issue), you MUST run a **deep, thorough security assessment of that PR's code before it is even considered for merge**. This app ships to the Google Play Store — a malicious or compromised change has a direct distribution channel to end users' devices. Treat every external PR as untrusted until proven safe.

Run the `/security-review` skill on the PR's diff, and additionally review by hand for:
- **Exfiltration / new network calls**: any new endpoints, hosts, sockets, `HttpURLConnection`/OkHttp/Retrofit usage, analytics, or telemetry — especially anything sending tokens, location, VIN, odometer, or account data off-device.
- **Secrets & credentials**: hardcoded keys, tokens, or attempts to read the user's TeslaMate credentials / stored tokens from new code paths.
- **Dangerous capabilities**: reflection, dynamic code loading (`DexClassLoader`, `Runtime.exec`, `ProcessBuilder`), `WebView` with `addJavascriptInterface` or `loadUrl` of remote content, native libs, `eval`-like behavior.
- **Permission & manifest changes**: new `<uses-permission>`, exported components, intent filters, `android:debuggable`, `usesCleartextTraffic`, or network-security-config edits.
- **Build/supply-chain changes**: new or bumped Gradle dependencies, changed repositories, modified Gradle/Fastlane/CI scripts, new Maven coordinates, or anything touching the F-Droid recipe or signing config.
- **Obfuscation**: base64/hex blobs, string-decoding routines, or deliberately unreadable logic that hides intent.

If anything looks suspicious or you cannot fully explain what a change does, STOP and report to the user — do NOT merge. A failing security assessment overrides "CI is green" and the no-wait-on-CI convention.

### 2. Investigate the Problem

Before writing any code:
1. Understand the issue completely
2. Search the codebase for relevant files using Glob and Grep
3. Read the relevant source files
4. If anything is unclear, add a comment to the issue asking for clarification (in the issue's original language)

### 3. Propose the Approach (get approval before coding)

Before creating a branch or editing any code, present the proposed fix to the user and wait for their approval:
- A short statement of the **root cause**.
- The **approach**: what you intend to change, which files, and why.
- The **scope**: what is in and what is deliberately left out (especially for broad issues — say what you are NOT touching).
- Any **trade-offs or open decisions**. If there is a meaningful scope or design choice, use `AskUserQuestion` so the user can pick.

Do NOT proceed to the next step until the user has approved the approach. The user may adjust the scope, pick a different option, or ask for more investigation first. Only skip this step if the user has explicitly told you to fix the issue end-to-end without checking in.

### 4. Create a Branch

Create a new branch for the fix:
```bash
git checkout -b fix/issue-<number>-<short-description>
```

Example: `fix/issue-42-dashboard-crash`

### 5. Apply the Fix

Edit the necessary files to fix the issue. Follow these principles:
- Keep changes minimal and focused on the issue
- Do not refactor unrelated code
- Add comments only where the logic isn't self-evident
- All code comments MUST be in English

At the end of the fixing process, ALWAYS update the CHANGELOG.md file with a short description of the fix and the related issue number.

### 6. Run Tests Locally

Run the test suite to verify the fix doesn't break anything:
```bash
./gradlew testDebugUnitTest
```

Fix any test failures before proceeding.

### 7. Commit and Push

Commit with a semantic commit message:
```bash
git add -A
git commit -m "fix: <description of fix> (#<issue-number>)"
git push -u origin fix/issue-<number>-<short-description>
```

### 8. Create Pull Request

Use `mcp__github__create_pull_request` to create a PR:
- **Title**: Same as the commit message (e.g., `fix: resolve dashboard crash (#42)`)
- **Body**: Must be in English and include:
  - Summary of the problem
  - Explanation of the fix
  - Reference to the issue: `Fixes #<issue-number>`
  - Test plan

Example PR body:
```markdown
## Summary
Brief description of what was fixed.

Fixes #42

## Root Cause
Explanation of why the bug occurred.

## Solution
What changes were made to fix it.

## Test Plan
- [ ] Manual testing steps
- [ ] Unit tests pass

Generated with [Claude Code](https://claude.com/claude-code)
```

### 9. Wait for CI

Monitor the GitHub Actions workflow:
```bash
gh run list --limit 5
gh run watch
```

If CI fails:
1. Read the failure logs: `gh run view <run-id> --log-failed`
2. Fix the issues locally
3. Commit and push the fixes
4. Wait for CI again

Do NOT proceed to merge until all checks pass.

### 10. Add Issue Comment

Once the PR is created, add a comment to the original issue (in the issue's original language) letting the reporter know a fix is in progress:
- Link to the PR
- Brief explanation of what was done

### 11. Merge the PR

Once CI passes, merge the PR:
```bash
gh pr merge <pr-number> --squash --delete-branch
```

Or use `mcp__github__merge_pull_request` with `merge_method: "squash"`.

### 12. Confirm Closure

Verify the issue was automatically closed by the `Fixes #<number>` reference. If not, close it manually and add a final comment (in the issue's original language) confirming the fix is released.

## Language Rules

| Context | Language |
|---------|----------|
| Issue comments | Same language as the original issue |
| Questions to clarify the issue | Same language as the original issue |
| Code comments | English |
| Commit messages | English |
| PR title and body | English |
| Code review responses | English |

## Voice and Style for Issue Comments

When writing comments on GitHub issues (status updates, clarifying questions, closing notes), match the maintainer's own informal voice. Specifically:

- Keep it casual and friendly, like a person typing a quick reply, not a formal report.
- Keep sentences short and plain. Say what happened and what's next, then stop.
- NEVER use em-dashes (—). Use a comma, a period, or parentheses instead.
- Avoid LLM tells: no "delve", no "I've gone ahead and", no long windups, no over-explaining, no bullet-point essays for a simple update.
- Don't pad with praise or filler. A simple "thanks for the report" is fine, skip the rest.
- It's ok to be a bit terse. Better short and human than long and polished.

This applies to issue/PR comments only. Code comments, commit messages, and PR bodies keep their normal technical style.

## Error Handling

- If the issue cannot be reproduced, comment on the issue asking for more details
- If the fix requires breaking changes, discuss in the issue before proceeding
- If tests cannot pass due to unrelated failures, note this and ask the user how to proceed
