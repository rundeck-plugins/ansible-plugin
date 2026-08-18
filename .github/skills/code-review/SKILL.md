---
name: code-review
description: Review pull requests for functional impact and breaking changes, with special focus on dependency version bumps (Renovate PRs) and their effect on end users. Use this whenever reviewing a pull request, and always for one that updates a dependency version.
---

<!-- AUTO-GENERATED from rundeck-plugins/.github/templates/code-review-skill.shared.md -- DO NOT EDIT. Run scripts/sync-code-review-skill.sh to update. -->

# Code Review Guide (rundeck-plugins)

This repo is part of the `rundeck-plugins` org. Full engineering guide: https://github.com/rundeck-plugins/.github/blob/main/CLAUDE.md

## Reviewing dependency version bumps (Renovate PRs)

Recognize these by: author `renovate[bot]`, branch name starting `renovate/`, title prefixed `[RUN-0000]`. Do not treat a version-bump PR as a rubber stamp just because the diff itself is small (often one line in `build.gradle`, `libs.versions.toml`, or `package.json`) - the risk is in what that one line pulls in, not in the size of the diff.

For every dependency version bump, answer explicitly in the review:

1. **What changed in the dependency between the old and new version?** This org's shared Renovate config disables `fetchChangeLogs`, so the PR body will not link release notes - look them up yourself (the dependency's GitHub releases, changelog, or Maven Central page). Call out any deprecations, removed APIs, or behavior changes documented there.
2. **Does this repo actually use the affected surface?** Search the codebase for imports/usages of the dependency's classes, methods, or config keys that the changelog flags as changed. A version bump touching an API this plugin never calls is low risk; a bump touching something the plugin actively depends on is not.
3. **Could this change functionality or break compatibility for end users of the plugin?** Frame this in terms of the actual Rundeck job/plugin behavior a customer would see, not just "the build still compiles." Backwards compatibility is a strong default in this org; flag anything that changes default behavior, error handling, or config semantics.
4. **What update type is this?** Major-version bumps in this org's shared Renovate config (`rundeck-plugins/.github/default.json`) already require manual approval from the Dependency Dashboard before the PR is even opened - so a major bump arriving as a PR is pre-flagged as higher-risk; look harder. Minor/patch bumps are usually lower risk but still merit the same checks above, especially for security-relevant dependencies.
5. **State a clear verdict**: safe to merge as-is, needs a specific manual check before merging (name it), or should not be merged without code changes.

This mirrors the standing manual prompt already used on these PRs: "evaluate this version bump against the code for impacts to functionality and/or breaking changes that may cause issues for end users." Apply it by default, not only when asked.

## General review guidance

- Optimize for customer impact; call out anything that changes behavior visible to a plugin's end users.
- Backwards compatibility is the default; deprecate before removing, and don't remove until the next major version.
- For plugin-type-specific build gotchas (JAR vs ZIP plugin packaging, `plugin.yaml` token processing, etc.), see PLUGINS_OVERVIEW.md: https://github.com/rundeck-plugins/.github/blob/main/PLUGINS_OVERVIEW.md
