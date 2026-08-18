<!-- AUTO-GENERATED from rundeck-plugins/.github/templates/copilot-instructions.shared.md -- DO NOT EDIT. Run scripts/sync-copilot-instructions.sh to update. -->

# GitHub Copilot Instructions

This repository is part of the `rundeck-plugins` organization and follows the org-wide engineering guide.

- Canonical guide: https://github.com/rundeck-plugins/.github/blob/main/CLAUDE.md
- Plugin overview: https://github.com/rundeck-plugins/.github/blob/main/PLUGINS_OVERVIEW.md

Read the canonical guide for the full context. The essentials below apply to work in this repo.

## Working agreements
- Optimize for customer impact first; connect technical choices to customer/business impact.
- Strive for backwards compatibility. Announce deprecations; don't remove until the next major version.
- Do not push to GitHub or create commits unless explicitly asked.
- Never add `Co-authored-by` / agent / Cursor trailers or email author flags to commits. Plain messages describing the change only.
- Commit messages and PR descriptions explain the "why" and the customer impact, not just the "what."
- Avoid emojis unless explicitly requested.
- Write full build logs to a `temp/` dir at the repo root using `YYYYMMDD-HHMMSS-<desc>.log` names; re-read logs instead of re-running builds repeatedly.

## Build and release conventions
- Baseline: Rundeck 6.0 (Grails 7 / Spring Boot 3). JAR plugins build on Java 17.
- Maven coordinates: group `com.rundeck.plugins`, published to PackageCloud (`PKGCLD_READ_TOKEN`).
- Versioning: Axion from git tags with `prefix = ''` (tags like `1.2.3`, no `v`).
- ZIP (script) plugins: package with Gradle `type: Jar` (archiveExtension `zip`), publish with `extension = 'jar'` and `pom.packaging = 'jar'`, and process `plugin.yaml` with `ReplaceTokens` (never `expand`).
- Groovy-based JAR plugins need the `groovy` Gradle plugin and `groovy-all` dependency or the jar ships with no compiled classes.

## Branch and CI conventions
- Default branch is `main`. Target CI triggers at `main`.
- Security scanning is centralized: `.github/workflows/snyk-scan.yml` calls the org reusable Snyk workflow (push/PR to `main`, weekly Monday 06:00 UTC, and manual dispatch).