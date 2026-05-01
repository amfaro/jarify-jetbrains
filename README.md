# Jarify for JetBrains

JetBrains IDE plugin providing **formatting** and **diagnostics** for SQL via the [`jarify`](https://github.com/amfaro/jarify) CLI. Mirrors [`jarify-vscode`](https://github.com/amfaro/jarify-vscode) — same CLI calls, same UX, different host.

## Features

- **Reformat Code / format on save** — pipes the active buffer through `jarify fmt --stdin-filename <file> -`
- **Inline diagnostics** — runs `jarify lint --format json --stdin-filename <file> -` and surfaces errors and warnings via the standard IntelliJ `ExternalAnnotator` pipeline
- **Auto-install** — on first project open, if `jarify` is not on `$PATH` the plugin offers to run `uv tool install jarify`

## Requirements

- JetBrains IDE 2024.1+ (IntelliJ IDEA Ultimate, DataGrip, etc. — anything with the bundled **Database Tools and SQL** plugin)
- [`uv`](https://docs.astral.sh/uv/) on `$PATH` if you want auto-install

## Settings

Open **Settings → Tools → Jarify**:

| Setting       | Default    | Description                                                |
|---------------|------------|------------------------------------------------------------|
| `Executable`  | `jarify`   | Path to the `jarify` binary (resolved from `$PATH`)        |
| `Config path` | _(empty)_  | Optional path to a `jarify.toml` configuration file        |

These map 1:1 to the `jarify.executable` and `jarify.configPath` settings in `jarify-vscode`.

## Format on Save

1. **Settings → Tools → Actions on Save** → enable **Reformat code** for SQL.
2. The Jarify formatter is registered as an `AsyncDocumentFormattingService`, so Reformat Code (`Ctrl+Alt+L` / `⌥⌘L`) and format-on-save both flow through `jarify fmt`.

## Development

Generate the Gradle wrapper once after clone (requires a system Gradle ≥ 8.10):

```bash
mise run wrapper
```

Then:

```bash
mise run run-ide   # launch a sandbox IDE with the plugin loaded
mise run build     # produce build/distributions/jarify-jetbrains-<ver>.zip
mise run verify    # run JetBrains plugin verifier against recommended IDEs
mise run test      # run unit tests
mise run publish   # publish to JetBrains Marketplace (needs JETBRAINS_MARKETPLACE_TOKEN)
```

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
