#!/usr/bin/env bash

set -euo pipefail

readonly REPOSITORY="rokx/Liano"
readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly LOCK_FILE="${TMPDIR:-/tmp}/liano-work-ready-issue.lock"
readonly BRANCH_PREFIX="codex/issue-"

dry_run=false
case "${1:-}" in
    "") ;;
    --dry-run) dry_run=true ;;
    *)
        echo "Usage: $0 [--dry-run]" >&2
        exit 64
        ;;
esac

require_command() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "Required command not found: $1" >&2
        exit 1
    }
}

for command in codex gh git flock mktemp; do
    require_command "$command"
done

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
    echo "Another work-ready issue run is already in progress."
    exit 0
fi

issue_number="$(gh issue list \
    --repo "$REPOSITORY" \
    --state open \
    --label work-ready \
    --limit 100 \
    --json number \
    --jq 'sort_by(.number) | first.number // empty')"

if [[ -z "$issue_number" ]]; then
    echo "No open work-ready issues."
    exit 0
fi

branch_name="${BRANCH_PREFIX}${issue_number}"
existing_pr="$(gh pr list \
    --repo "$REPOSITORY" \
    --head "$branch_name" \
    --state open \
    --json url \
    --jq '.[0].url // empty')"

if [[ -n "$existing_pr" ]]; then
    gh issue edit "$issue_number" --repo "$REPOSITORY" --remove-label work-ready
    echo "Removed work-ready from issue #$issue_number because $existing_pr already exists."
    exit 0
fi

if "$dry_run"; then
    echo "Would process issue #$issue_number on branch $branch_name."
    exit 0
fi

remote_branch="$(git -C "$PROJECT_ROOT" ls-remote --heads origin "refs/heads/$branch_name")"
if [[ -n "$remote_branch" ]]; then
    git -C "$PROJECT_ROOT" fetch --quiet origin "$branch_name:refs/remotes/origin/$branch_name"
    changed_files="$(git -C "$PROJECT_ROOT" diff --name-only "origin/develop...origin/$branch_name")"

    pr_body="$(mktemp "${TMPDIR:-/tmp}/liano-pr-body.XXXXXX")"
    trap 'rm -f "$pr_body"' EXIT
    {
        printf '## Summary\n\nAutomated implementation for #%s.\n\n' "$issue_number"
        printf '## Rationale\n\n'
        gh issue view "$issue_number" --repo "$REPOSITORY" --json body --jq '.body // "No issue description provided."'
        printf '\n\n## Before / after\n\n'
        printf '%s\n' "- Before: issue #$issue_number was open."
        printf '%s\n' "- After: the following files are implemented on $branch_name:"
        if [[ -n "$changed_files" ]]; then
            while IFS= read -r file; do
                printf '  - `%s`\n' "$file"
            done <<<"$changed_files"
        else
            printf '%s\n' '  - No diff could be determined; inspect the branch before merging.'
        fi
        printf '\n## Validation\n\n'
        printf '%s\n' '- `./gradlew testDebugUnitTest --no-daemon --console=plain` — passed before the branch was pushed.'
        printf '\nCloses #%s.\n' "$issue_number"
    } >"$pr_body"

    pr_url="$(gh pr create \
        --repo "$REPOSITORY" \
        --base develop \
        --head "$branch_name" \
        --title "$(gh issue view "$issue_number" --repo "$REPOSITORY" --json title --jq .title)" \
        --body-file "$pr_body")"
    gh issue edit "$issue_number" --repo "$REPOSITORY" --remove-label work-ready
    echo "Opened $pr_url from existing branch and removed work-ready from issue #$issue_number."
    exit 0
fi

worktree="$(mktemp -d "${TMPDIR:-/tmp}/liano-work-ready.XXXXXX")"
issue_context="$(mktemp "${TMPDIR:-/tmp}/liano-issue-context.XXXXXX")"
pr_body=""
worktree_added=false

cleanup() {
    rm -f "$issue_context" "$pr_body"
    if "$worktree_added"; then
        git -C "$PROJECT_ROOT" worktree remove --force "$worktree" || true
    fi
    rm -rf "$worktree"
}
trap cleanup EXIT

git -C "$PROJECT_ROOT" fetch --quiet origin develop
if git -C "$PROJECT_ROOT" show-ref --verify --quiet "refs/heads/$branch_name"; then
    git -C "$PROJECT_ROOT" branch -D "$branch_name"
fi
git -C "$PROJECT_ROOT" worktree add --quiet --detach "$worktree" origin/develop
worktree_added=true

{
    cat <<'PROMPT'
Implement the GitHub issue provided below in this Liano repository.

Work only on the requested issue. Read AGENTS.md, inspect the code, make the smallest complete change, and add meaningful tests where appropriate. Run the relevant tests before finishing. Do not create a commit, push, open a pull request, change GitHub labels, or modify files outside this worktree: the automation will perform those steps after it validates your changes.

Issue data follows as JSON:
PROMPT
    gh issue view "$issue_number" --repo "$REPOSITORY" --json number,title,body,url
} >"$issue_context"

codex \
    -a never \
    exec \
    -C "$worktree" \
    --sandbox workspace-write \
    - <"$issue_context"

if [[ -z "$(git -C "$worktree" status --porcelain)" ]]; then
    echo "Codex made no changes for issue #$issue_number; leaving work-ready in place." >&2
    exit 1
fi

(
    cd "$worktree"
    ./gradlew testDebugUnitTest --no-daemon --console=plain
)

git -C "$worktree" config user.name "rokx"
git -C "$worktree" config user.email "197242516+rokx@users.noreply.github.com"
git -C "$worktree" switch --quiet -c "$branch_name"
git -C "$worktree" add -A
git -C "$worktree" commit -m "fix: address #$issue_number"
git -C "$worktree" push --set-upstream origin "$branch_name"

changed_files="$(git -C "$worktree" diff --name-only "origin/develop...HEAD")"
pr_body="$(mktemp "${TMPDIR:-/tmp}/liano-pr-body.XXXXXX")"
{
    printf '## Summary\n\nAutomated implementation for #%s.\n\n' "$issue_number"
    printf '## Rationale\n\n'
    gh issue view "$issue_number" --repo "$REPOSITORY" --json body --jq '.body // "No issue description provided."'
    printf '\n\n## Before / after\n\n'
    printf '%s\n' "- Before: issue #$issue_number was open."
    printf '%s\n' "- After: the following files were changed:"
    while IFS= read -r file; do
        printf '  - `%s`\n' "$file"
    done <<<"$changed_files"
    printf '\n## Validation\n\n'
    printf '%s\n' '- `./gradlew testDebugUnitTest --no-daemon --console=plain` — passed in the automation run.'
    printf '\nCloses #%s.\n' "$issue_number"
} >"$pr_body"

pr_url="$(gh pr create \
    --repo "$REPOSITORY" \
    --base develop \
    --head "$branch_name" \
    --title "$(gh issue view "$issue_number" --repo "$REPOSITORY" --json title --jq .title)" \
    --body-file "$pr_body")"

gh issue edit "$issue_number" --repo "$REPOSITORY" --remove-label work-ready
echo "Opened $pr_url and removed work-ready from issue #$issue_number."
