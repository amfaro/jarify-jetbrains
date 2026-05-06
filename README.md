# Jarify for JetBrains

<!-- Plugin description -->
JetBrains IDE plugin providing **formatting** and **diagnostics** for SQL via the [`jarify`](https://github.com/amfaro/jarify) CLI. Mirrors [`jarify-vscode`](https://github.com/amfaro/jarify-vscode) — same CLI calls, same UX, different host.

> **⚠️ DuckDB only.** This plugin is a thin wrapper around [`jarify`](https://github.com/amfaro/jarify), which formats and lints **DuckDB SQL exclusively**. Using it with PostgreSQL, MySQL, SQLite, or any other SQL dialect will produce incorrect formatting and misleading diagnostics.

## Features

- **Reformat Code / format on save** — pipes the active buffer through `jarify fmt --stdin-filename <file> -`
- **Inline diagnostics** — runs `jarify lint --format json --stdin-filename <file> -` and surfaces errors and warnings via the standard IntelliJ `ExternalAnnotator` pipeline
- **Auto-install** — on first project open, if `jarify` is not on `$PATH` the plugin offers to run `uv tool install jarify`
<!-- Plugin description end -->

## Requirements

- JetBrains IDE 2024.1+ (IntelliJ IDEA Ultimate, DataGrip, etc. — anything with the bundled **Database Tools and SQL** plugin)
- [`uv`](https://docs.astral.sh/uv/) on `$PATH` if you want auto-install

## Getting Started

### 1. Install the plugin

#### From JetBrains Marketplace

After Marketplace approval, install **Jarify** from your IDE:

1. Open **Settings → Plugins → Marketplace**.
2. Search for `Jarify`.
3. Click **Install** and restart the IDE if prompted.

If the Marketplace listing is not visible yet, install from a local build.

#### From a local build

Build the plugin ZIP:

```bash
mise run build
```

This produces `build/distributions/jarify-jetbrains-<version>.zip`.

Install it in the IDE:

1. Open **Settings → Plugins**.
2. Click the gear icon (**⚙**) and choose **Install Plugin from Disk**.
3. Select `build/distributions/jarify-jetbrains-<version>.zip`.
4. Restart the IDE if prompted.

### 2. Install `jarify`

On first project open, the plugin checks for `jarify` and offers to install it automatically via `uv tool install jarify`. Accept the prompt, or install manually:

```bash
uv tool install jarify
```

### 3. Enable format on save _(optional)_

Formatting on save is controlled by DataGrip/IntelliJ, not the plugin. You must opt in:

> **Settings → Tools → Actions on Save** → enable **Reformat code**

Important: JetBrains runs Actions on Save on **explicit save** (`⌘S` / `Ctrl+S`). Plain autosave does not trigger it.

Without this step, the formatter won't run on save — but **Reformat Code (`⌥⌘L` / `Ctrl+Alt+L`)** works immediately with no extra configuration.

### 4. Lint annotations

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
mise run publish          # publish update (needs Marketplace listing + token)
mise run release:prepare  # prepare or update automated release PR
```

Release automation mirrors `../jarify`:

- `prepare-release.yml` opens or updates a `release/vX.Y.Z` PR
- `publish.yml` runs on `main` pushes that change `gradle.properties` — normally the merged release PR
- `publishPlugin` cannot create the first Marketplace listing; first upload must
  be manual

Full maintainer runbook: [`docs/releasing.md`](docs/releasing.md), including
[first Marketplace upload](docs/releasing.md#first-marketplace-upload) and
[publish failure troubleshooting](docs/releasing.md#troubleshooting-publish-failures).

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

## License

MIT. See [`LICENSE`](LICENSE).
