# Contributing to ClassicLauncher

## Commit Messages

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>[optional scope]: <description>
```

**Types:** `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `ci`, `build`, `style`

Append `!` after the type/scope for breaking changes:

```
refactor(context)!: rename default data directory to "classiclauncher"
```

## Branch Naming

Branches follow the pattern `<type>/<short-description>`:

```
feat/game-selector
fix/null-profile-crash
refactor/rename-launcher-name
```

## Pull Request Labels

Available labels:

| Label           | Purpose                                              |
|-----------------|------------------------------------------------------|
| `breaking`      | Breaking changes to the API, data paths, or behavior |
| `enhancement`   | New features                                         |
| `bug`           | Bug fixes                                            |
| `extensions`    | Extension system changes                             |
| `documentation` | Documentation updates                                |
| `maintenance`   | General maintenance and chores                       |
| `dependencies`  | Dependency updates                                   |

## Breaking Changes

When a PR introduces a breaking change (API changes, renamed data directories, removed features, changed defaults, etc.):

1. **Add the `breaking` label** to the PR
2. **Include a `## Breaking Changes` section** in the PR body describing:
   - What changed
   - Why it changed
   - Migration steps for users or extension developers

The release workflow automatically collects all `## Breaking Changes` sections from PRs labeled `breaking` and prepends them to the GitHub Release notes, giving users a clear upgrade guide with each release.