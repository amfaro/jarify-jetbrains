# Jarify for JetBrains

JetBrains IDE plugin providing **formatting** and **diagnostics** for SQL via the [`jarify`](https://github.com/amfaro/jarify) CLI. Mirrors [`jarify-vscode`](https://github.com/amfaro/jarify-vscode) — same CLI calls, same UX, different host.

## Features

- **Reformat Code / format on save** — pipes the active buffer through `jarify fmt --stdin-filename <file> -`
- **Inline diagnostics** — runs `jarify lint --format json --stdin-filename <file> -` and surfaces errors and warnings via the standard IntelliJ `ExternalAnnotator` pipeline
- **Auto-install** — on first project open, if `jarify` is not on `$PATH` the plugin offers to run `uv tool install jarify`

## Requirements

- JetBrains IDE 2024.1+ (IntelliJ IDEA Ultimate, DataGrip, etc. — anything with the bundled **Database Tools and SQL** plugin)
- [`uv`](https://docs.astral.sh/uv/) on `$PATH` if you want auto-install

## Getting Started

### 1. Build the plugin zip

```bash
mise run build
```

This produces `build/distributions/jarify-jetbrains-<ver>.zip`.

### 2. Install the plugin

**Settings → Plugins → ⚙ → Install Plugin from Disk** → select the `.zip` from `build/distributions/`.

### 3. Install `jarify`

On first project open, the plugin checks for `jarify` and offers to install it automatically via `uv tool install jarify`. Accept the prompt, or install manually:

```bash
uv tool install jarify
```

### 4. Enable format on save _(optional)_

Formatting on save is controlled by DataGrip/IntelliJ, not the plugin. You must opt in:

> **Settings → Tools → Actions on Save** → enable **Reformat code**

Important: JetBrains runs Actions on Save on **explicit save** (`⌘S` / `Ctrl+S`). Plain autosave does not trigger it.

Without this step, the formatter won't run on save — but **Reformat Code (`⌥⌘L` / `Ctrl+Alt+L`)** works immediately with no extra configuration.

### 5. Lint annotations

No setup required. Inline warnings and errors appear automatically as you edit any SQL file.

## Settings

Open **Settings → Tools → Jarify**:

| Setting       | Default    | Description                                                |
|---------------|------------|------------------------------------------------------------|
| `Executable`  | `jarify`   | Path to the `jarify` binary (resolved from `$PATH`)        |
| `Config path` | _(empty)_  | Optional path to a `jarify.toml` configuration file        |

These map 1:1 to the `jarify.executable` and `jarify.configPath` settings in `jarify-vscode`.

## Development

```bash
mise run run-ide          # launch a sandbox IDE with the plugin loaded
mise run build            # produce build/distributions/*.zip
mise run clean            # wipe Gradle build outputs (use before build if zip looks stale)
mise run verify           # run JetBrains plugin verifier against configured IDE
mise run test             # run unit tests
mise run publish          # publish to JetBrains Marketplace (needs JETBRAINS_MARKETPLACE_TOKEN)
mise run release:prepare  # prepare or update automated release PR
```

Release automation mirrors `../jarify`:
- `prepare-release.yml` opens or updates a `release/vX.Y.Z` PR
- `publish.yml` runs on `main` pushes that change `gradle.properties` — normally the merged release PR
- first Marketplace publication still must be done manually

Full maintainer runbook: [`docs/releasing.md`](docs/releasing.md).

The plugin requires JDK 17. `mise.toml` pins `temurin-17`.

## Architecture

| Concern        | Class                                      | CLI invocation                                                       |
|----------------|--------------------------------------------|----------------------------------------------------------------------|
| Formatting     | `JarifyAsyncFormattingService`             | `jarify fmt --stdin-filename <file> -`                               |
| Diagnostics    | `JarifyExternalAnnotator`                  | `jarify lint --format json --stdin-filename <file> -`                |
| Auto-install   | `JarifyInstallStartupActivity`             | `jarify --version` then `uv tool install jarify`                     |
| Settings       | `JarifySettings` + `JarifyConfigurable`    | —                                                                    |
| Process helper | `JarifyCli`                                | builds `--config <path>` args, spawns process, pipes stdin           |

Issue tracker: [amfaro/jarify#263](https://github.com/amfaro/jarify/issues/263).
