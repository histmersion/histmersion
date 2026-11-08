# Boundaries review (2026-11-08)

`architecture/boundaries.tsv` now lists adapters only: a file is there when translating to or from the outside is its job. It had 292 files; it has 260 (249 kept, 43 dropped, 11 added, 5 given another kind). Every row now says in one sentence what the file translates.

## What qualifies, by kind

- **sql (101 files)**: a repository's SQL, its row codecs, and the column codecs, pools, migrations and fragment builders that sit directly on JDBC.
- **json (47 files)**: a JSON, YAML, CSV, XML or tar codec, including the parsers of upstream answers in the plugins and the `.hmx` bundle reader.
- **cli-args (36 files)**: the `histmersion` argument parsers and the terminal writer.
- **process (27 files)**: file and process adapters: disk stores, byte and blob sources, child-process runners, the plugin-resolver's jar and lib-directory access, the AI provider CLIs' argument and output translation.
- **http (26 files)**: HTTP clients and transports, the web UI's API client, and the Tapir `*Api` files that declare request and response bodies.
- **config (12 files)**: the HOCON readers, layers, writers and the settings-file readers.
- **spi (6 files)**: the conversions between the plugin API and the host model.
- **dom (5 files)**: the browser edge: address bar, localStorage, clipboard, theme, URL-hash field codec.

## Files I was unsure about

- `Codex (server)`: builds the codex CLI arguments and reads its output, but also prunes the Codex home. Kept: the translation is most of the file; the pruning is a G23 follow-up.
- `ClaudeCode (server)`: builds the claude CLI arguments and reads its stream-json output. Added, as the twin of Codex.
- `Opencode (server)`: the same for the opencode CLI. Added with the other two.
- `OutboundTls (plugin-host)`: builds a TLS trust store from the JDK, the OS keychain and a PEM file. Kept: it reads the outside trust sources and nothing else.
- `DirSize (cli)`: walks a directory to measure it. Kept as a file adapter; it does nothing but read the file system.
- `FileFormat (cli)`: splits a note file into frontmatter and body for `lint --file`. Kept as a parser of the outside file; it is small and pure.
- `CalibreCmd (cli)`: parses `histmersion calibre` arguments, 888 lines, with much dispatch. Kept because the arguments are still its job; the dispatch may belong in a service.
- `ConfigSchema (config)`: reads reference.conf into the list of settings. Kept as a reader of the config file format.
- `UserPaths (config)`: works out the data and config directories from the environment. Dropped: it computes paths and touches no file.
- `FirstPartySettings (plugin-host)`: the table of host keys each first-party plugin reads, and the rebuild rule. Dropped: it is policy over config, not a reader.
- `HostServices (plugin-host)`: the record of services lent to plugins. Dropped: it holds services and translates nothing.
- `HostAiProvider (server)` and `HostProvided (server)`: the host's own AI runtimes registered as if they were plugins. Dropped: registration shims, no value is converted.
- `HttpPlugin (server)`: lends the host's HTTP client to plugins as the plugin API's client. Kept: it converts the types at the SPI.
- `PolicyHttp (plugin-host)`: the acquisition client with User-Agent, retries and Retry-After. Kept as an HTTP client; the retry policy is the risk.
- `CoreApi (upstreams)`: calls CORE's search and returns JSON. Kept as an HTTP client of one upstream.
- `SttpHttp (server)`: offers the acquisition client as get, bytes and once. Added: it is the HTTP edge of acquisition.
- `Api files (api)`, 13 of them (`AcquireApi`, `AgentsApi`, `EventsApi` and the rest): Tapir endpoint definitions with their request and response bodies. Kept as the wire shape of the routes; they are not the Controllers, which stay out.
- `ArticlePatches (cli)`: builds an article patch from flags, but reads a `--json` file. Dropped, for the same reason you gave for `Patches`.
- `AddOptions (cli)`: the options record of `plugin add`. Dropped: a data holder.
- `FieldCodec (web)`: encodes a UI field to and from the URL hash and storage strings. Kept, and moved to dom.
- `MapPrefs (web)`: viewer map options that carry their own storage codec. Dropped: the codec is a small part of a preferences type.
- `UiState (web)`: the UI state, mirrored to the hash and localStorage. Dropped: state, with the edge in a corner of it.
- `TimelineView (web)` and `StandalonePage (web)`: a renderer and a page entry point that use many DOM calls. Dropped as renderers and wiring.
- `JsonRead (core)`: reads fields from a stored JSON document. Kept, though pure.
- `PleiadesLinks (plugins/media)`: picks Commons links out of a Pleiades record. Kept as a reader of an upstream answer.
- `Wire (plugin-testkit)`: writes and reads the recorded HTTP exchange files. Kept as a file format codec.
- `WireEnumValues (api)` and `WireEnumValues (values)`: the spelling table of an enum's wire form. Kept: it is the wire spelling itself, not logic.
- `SeedNoteFiles (server)` and `ServerToolFiles (server)`: read note folders and the MCP ingest file under the allowed directory. Kept as file readers.
- `Store (server)`, `Blob (server)`, `TextFiles (files)`, `LocalFiles (plugin-api)`: the file-access seams. Added, in line with `Storage` and `ByteSource`.
- `Subprocess (plugin-vaults)`: runs a vault's CLI with a time limit. Added as a process adapter.
- `BundleReader (files)` and `BundleTar (files)`: read and write the `.hmx` bundle stream. Added as codecs of an outside format.
- `Keyset (db)`: builds the keyset-paging SQL. Added next to the repositories.
- `Geocoder (db)` and `FilterSql (db)`: place lookup and filter-to-SQL, with ranking and logic around the SQL. Kept: SQL is most of each.
- `Supervisor (server)`: starts and watches PostgreSQL and the OCR converter. Dropped: a lifecycle service that starts processes, like Bootstrap.
- `Calibre (plugins/calibre)`: reads and writes the Calibre library through calibredb. Not added: it is a library service over `Shell`, which is listed.

## Services that do I/O directly (dropped; each is a G23 finding)

- **Startup**: `Bootstrap (server)` reads config files and downloads; `Supervisor (server)` starts the bundled PostgreSQL and converter.
- **Agents**: `AgentLease (server)` writes and checks lock files; `PlanPools (server)` reads the provider CLIs' usage files and runs them; `ClaudeCodeClient (server)` and `CodexExecClient (server)` run their CLI and read its output files; `AgentDoctor (server)` calls provider endpoints and runs the CLIs.
- **Maps**: `BasemapService (server)` downloads and writes basemap files; `OnDemandTiles (server)` cuts tiles from files on disk.
- **Gazetteer**: `GazetteerPaths (server)` resolves paths and reads the pin manifest.
- **Plugins**: `RelinkPlugins (server)` reads jars and writes the plugin directory; `DependencyResolver (plugin-resolver)` reads the kit and writes the lib directory; `PluginHarness (plugin-testkit)` replays recorded exchanges from files.
- **CLI commands**: `Calibre`, `Notes`, `MediaCovers`, `Patches` and `ArticlePatches` read files or run `calibredb` while building a request; `PluginOps` copies jars and runs the relink scripts; `PluginAuthoring` writes a new plugin project.

Dropped with no I/O of their own (pure data or policy, so no G23): `Environment`, `LoadedConfig`, `ConfigReload`, `ConfigSource`, `SettingsService`, `LocalInstall`, `PluginTemplate`, `PluginTemplateKinds`, `ManagerLight`, `FilterBar`, `SetupPage`, `Persist`, `Main`, and the ones named above.

## G44 after the change

G44_PLACEHOLDER
