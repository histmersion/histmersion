# Plugins: an SPI, typed config, native image, user plugins, local builds (research, 2026-10-18)

**Status:** research and design proposal for #2017, with two proof-of-concept spikes (§10). Phase 1
(the SPI, the config contract, secrets and the built-in vaults) is built: §15 says what was built and
where it differs from this proposal. Phase 2 (source fetchers; the unauthorised fallback removed) is
built: §16. The in-place config reload (§3.5) and the hot component graph (§3.6) are built: §17. The
install/build side — the relink kit, JVM mode, `histmersion plugin …`, the local build at setup and the
optional signing (phases 3–5) — is built: §18. Phase 6's first part (media collections and event
catalogues as plugins, the plugin list in the web About window) is built: §19. Decisions D4 (one plugin
per open-access index) and D5 (the local build is the default) are built: §20. §18's follow-ups — CI
kits, the TCC check, GPG verification at each start — are built: §21. AI providers as plugins: the
design is §22, what was built §23. The
spike code is in `spike/plugins/`, a standalone sbt build that
the root build does not aggregate. Measurements were taken on this machine: Apple M4 Pro, 14 cores,
48 GB RAM, macOS 26.7, GraalVM CE 25.0.1 (the `.sdkmanrc` pin), sbt 2.0.7. Statements I could not
check are marked **(unverified)**. Everything quoted from the web was fetched on 2026-10-18.

The task asks seven questions:

1. What should the SPI look like? (§2)
2. How should plugin config be read? (§3)
3. How do plugins work in a closed-world native image? (§4)
4. What does the first plugin kind, source fetching, look like? (§5)
5. Should the other providers use the same SPI? (§6)
6. How does a user add a plugin of their own? (§7)
7. Should the native image be built locally at setup? (§8)

The coordinator added an eighth: a signing key, so that every local build has the same origin (§9).

---

## 0. The recommendation in brief

- **The SPI.** Add a small module, `modules/plugin-api`, that depends only on sconfig and
  kindlings-sconfig-derivation. **One** ServiceLoader service, `Plugin` (decided 2026-10-18, §2.2):
  a plugin defines its config type and reader as path-dependent members, `Main` decodes its block
  and calls `register(config, ctx)`, and the plugin adds to whichever registries of the app context
  it extends (source fetchers, media collections, event catalogues, …). The host cuts out `histmersion.plugins.<id> { … }`,
  layers it over the plugin's own defaults resource, type-checks it by key, decodes it with the
  plugin's reader and passes in the typed value. A plugin never sees raw HOCON. Spike A proves all
  of this on the JVM and in a native image.
- **Native image.** Use a hybrid:
  - Every first-party plugin is on the image classpath, and config enables it at run time.
  - Third-party ("user") plugins are chosen at build time: adding one triggers a **relink**.
    A relink re-runs `native-image` over a kept *relink kit* (the server's Sloth-patched jars) plus
    the plugin jars. It needs no sbt and no Scala compile.

  GraalVM 25 registers `META-INF/services` providers by itself (`-H:+UseServiceLoaderFeature`, on by
  default). It also reads each jar's own `META-INF/native-image/<group>/<artifact>/reachability-metadata.json`.
  Spike A shows both: the provider needed no metadata entry, and the plugin's reflection and
  resource entries came from its own jar.
- **Source fetching first.** Split today's `OaFetch` chain into ordered fetcher plugins, each
  declaring `Access.Open | Subscription | Unauthorised`. The host refuses to run an `Unauthorised`
  fetcher unless config allows it, and never runs one on the legal-only path. No unauthorised fetcher
  lives in, or is named by, this repository (decided 2026-10-18, §14). ResearchGate gets no fetcher. A JSTOR fetcher
  is not possible inside JSTOR's terms (§11).
- **Media collections next.** The media collections already have a near-SPI (`media/Collection`).
  Event catalogues come third. Gazetteer sources stay a data-level extension: the Linked Places
  Format (LPF) directory already lets a user add a gazetteer without code (§6).
- **Local build at setup is feasible** on a 16 GB+ Mac or Linux box:
  - The real server image took **1 min 41 s**, with a **peak RSS of 10.97 GB** and a 208 MB binary.
  - A cold run of the whole `server/nativeImage` task (Sloth patching included) took **3 min 56 s**.
  - A from-source cold compile in an isolated home took **2 min 4 s** and peaked at **4.55 GB**.

  A locally linked binary carries no quarantine attribute and is already ad-hoc signed by the
  linker, so it needs no notarisation. Use GraalVM **CE**, not Oracle GraalVM: CE is GPLv2 with the
  Classpath Exception, which the native-image runtime code (Substrate VM) also carries. CE is free
  to download, bundle and redistribute. The fallback is JVM mode on the same kit with the bundled JDK.
- **Signing.** On macOS, create a self-signed X.509 code-signing identity at setup. The spike shows
  that its designated requirement (DR) stays identical across builds, while an ad-hoc DR is a cdhash
  that changes on every build. It also shows that codesign signs with the identity **untrusted**, so
  setup does not need to change trust settings. Optionally, a GPG key signs Histmersion's own
  provenance (the plugin-set manifest and the built binaries) and verifies third-party plugin jars
  against their Maven Central `.asc`. macOS ignores GPG. On Linux, GPG is the only identity (§9).

---

## 1. What exists today

| area | where | shape today | plugin-readiness |
|---|---|---|---|
| OA acquisition | `server/acquire/Clients.scala` `OaFetch` | one class; a `LazyList` of steps: Unpaywall → CORE → OpenAlex OA → Europe PMC/PMC PDF → arXiv → repository landing pages (Zenodo API, `citation_pdf_url`, DataCite) → PMC JATS text → an unauthorised fallback, off by default, which `fetchLegal` skipped (removed from the repository in phase 2, §16) | the steps were private methods sharing two lazy records (`OaRecords`, `PmcLookup`); phase 2 split them into fetcher plugins (§16) |
| archive.org | `ArchiveOrg` (`ArchiveClient`: search / info / download) | a separate surface (`histmersion archive …`), not in the DOI chain | a fetcher by identifier, not by DOI. The `WorkKey` needs an `archive` id (§5.1) |
| Outbound HTTP | `acquire/Http.scala` (`SttpHttp`), `media/MediaHttp.scala` (`Polite`), `core/HttpPolicy` | one policy (#1946): User-Agent without an e-mail, Retry-After, back-off, bot challenges never worked around | **the host service a plugin must use** (`PluginHttp`, §2) |
| Media | `server/media/Collections.scala` `trait Collection`; `MediaImporter.of` (a `match` over names) | Met, Cleveland, AIC, LoC, Open Context are `Collection`s. Wikidata, depicts and Pleiades are bespoke runs | `Collection` is already an SPI in all but name |
| Event catalogues | `core/EventProviders` (GVP, NOAA, Wikidata), `server/events/EventCatalogHandlers` | pure URL/parse objects; the handler switches on the catalogue | a plugin kind once the record type (`CatalogRecord`) is in the API |
| Gazetteer | `server/importers/GazetteerLoad` (`geonames`, `wikidata`, `tgn` pinned extracts; `lpf` directory), Pleiades/Natural Earth seeds, Cliopatria, PeriodO, AWMC, ChronOntology importers | data pipelines; extracts are pinned by sha256 | the `lpf` directory is already a no-code extension point |
| Config | `core/Config.scala`, `core/UserConfig.scala` (`ConfigLayers`) | sconfig + `derives ConfigReader` (Kindlings). Layers: env > `histmersion.conf` > `reference.conf`. Unknown keys warn. `typeCheck` names a mistyped key because "the derived readers do not say which key a wrong type is at". Lenient `Int/Long/Double/Boolean` givens accept numeric strings from env | the contract a plugin block must follow (§3); spike A hit both gotchas |
| Native build | `build.sbt` `nativeImage` (custom task), `slothPatch`, `checkNoLegacyNativeConfigs`; `scripts/bundle/server.sh` | `native-image -cp <sloth-patched jars> Main`; per-module `reachability-metadata.json` from the tracing agent | plugin jars join `-cp`; their metadata travels inside the jar |
| Install | `scripts/bundle/install-macos.sh`, `codesign-identity-macos.sh`, `.github/workflows/bundle.yml` | `just install` already **builds locally** (`just bundle` → `server.sh`). CI builds release bundles for macOS-arm64 and Linux x64/arm64. The installer strips quarantine and signs with "Histmersion Local" when that identity exists | §8.6 says what changes |

## 2. The SPI

### 2.1 Placement

The task says "in core". I propose a **separate module** `modules/plugin-api`, which `coreJVM`
re-exports and depends on. My reasons:

- A plugin author compiles against it. If it lived in `core`, every plugin would pull Tapir, Circe,
  Kindlings-Circe and the whole domain model onto its compile classpath, and its binary
  compatibility would be tied to the most-changed module of the repo.
- Its dependencies stay at sconfig + kindlings-sconfig-derivation, which the host already ships.
- It has its own `Spi.Version`. That number changes only when the API changes, not on every core
  refactor.

If the user prefers one module, putting the package `com.histmersion.plugin` inside `core` works
just as well technically (open decision D1).

### 2.2 Decided (user, 2026-10-18): ONE service type; a plugin registers into the app context

A plugin is not "a source fetcher" or "a media collection": one plugin may add to several parts of
the app at once (a portal plugin can bring a source fetcher, a media collection and an event
catalogue that share one client and one config). So there is **one** ServiceLoader service,
`com.histmersion.plugin.Plugin`. Each plugin defines its config type and its reader as
**path-dependent** members; `Main` parses the plugin's config block with that reader and passes the
typed value, together with the app context, to `register`. The plugin registers whatever it
contributes into the context's registries. This supersedes spike A's one-abstract-class-per-kind
shape (§2.2 as first written) and design rule "one ServiceLoader service per kind" (§2.3).

```scala
package com.histmersion.plugin

/** id = the config key histmersion.plugins.<id> (kebab-case); spiVersion = the SPI it was built for */
final case class PluginInfo(id: String, version: String, spiVersion: Int)

trait Plugin:
  type Config
  def info: PluginInfo
  given configReader: ConfigReader[Config]             // path-dependent: p.Config, p.configReader
  /** its defaults: own name per plugin — never reference.conf */
  def defaultsResource: String = s"META-INF/histmersion/plugins/${info.id}.conf"
  /** Called once, only when enabled, after Main decoded `histmersion.plugins.<id>` into Config. */
  def register(config: Config, ctx: PluginContext): Either[String, Unit]

/** What the app lends a plugin, and where it plugs in. Each registry is one extension point. */
trait PluginContext:
  def http: PluginHttp                                  // the host's HttpPolicy client
  def log(message: String): Unit
  def sourceFetchers: Registry[SourceFetcher]           // acquisition chain
  def mediaCollections: Registry[MediaCollection]       // media import providers
  def eventCatalogues: Registry[EventCatalogue]         // event catalogue importers
  // later: gazetteer sources, lints, exporters … — a new extension point is a new registry here,
  // not a new service type, so existing plugin jars keep loading (SPI minor version).

trait Registry[A]:
  def add(id: String, a: A): Either[String, Unit]       // refuses a duplicate id per registry

/** A plugin author derives the reader at the definition site; the provider stays a class with a
  * public no-argument constructor, as ServiceLoader requires. */
final case class PortalConfig(baseUrl: String, perHost: Int, licences: List[String]) derives ConfigReader

final class PortalPlugin extends Plugin:
  type Config = PortalConfig
  given configReader: ConfigReader[PortalConfig] = summon
  val info = PluginInfo("portal", "0.1.0", Spi.Version)
  def register(c: PortalConfig, ctx: PluginContext) =
    val client = PortalClient(c, ctx.http)
    for
      _ <- ctx.sourceFetchers.add("portal", PortalFetcher(client))
      _ <- ctx.mediaCollections.add("portal", PortalMedia(client, c.licences))
    yield ()
```

`Main` (one generic loop, no per-kind code, no casts — the decoded value has type `p.Config`):

```scala
def load(p: Plugin, conf: Config, ctx: PluginContext): Either[String, Unit] =
  for
    block <- PluginConfig.layered(p, conf)                     // defaults resource ⊕ user block, types checked (§3)
    typed <- p.configReader.from(block.root).left.map(describe(p.info.id, _))   // : p.Config
    _     <- p.register(typed, ctx)
  yield ()

ServiceLoader.load(classOf[Plugin]).asScala
  .filter(p => enabled(p.info.id))
  .foreach(p => load(p, conf, ctx).left.foreach(err => log.warn(s"plugin ${p.info.id}: $err")))
```

Consequences: the registries are what the rest of the app reads (the acquisition chain asks
`sourceFetchers`, `media import <provider>` asks `mediaCollections`); registration order is the
order in config (`histmersion.plugins.order`), then by id; a plugin whose `register` fails
contributes nothing (registries are staged and committed per plugin); the native-image story (§4) is
unchanged — still one `META-INF/services/com.histmersion.plugin.Plugin` per jar, auto-registered by
GraalVM 25, plus the plugin's own reachability metadata.

### 2.2a Code sketch as built in spike A (superseded by §2.2; kept for the evidence)


```scala
package com.histmersion.plugin

enum PluginKind:
  case SourceFetcher, MediaCollection, EventCatalogue, Gazetteer

/** id = the config key histmersion.plugins.<id> (kebab-case); spiVersion = the SPI it was built for */
final case class PluginInfo(id: String, kind: PluginKind, version: String, spiVersion: Int)

trait Plugin:
  type Config
  def info: PluginInfo
  def configReader: ConfigReader[Config]
  /** its defaults: own name per plugin — never reference.conf */
  def defaultsResource: String = s"META-INF/histmersion/plugins/${info.id}.conf"

trait PluginHttp:                       // the host's HttpPolicy client; plugins never open sockets
  def get(url: String, headers: Map[String, String] = Map.empty): Either[String, Array[Byte]]
trait HostServices:
  def http: PluginHttp
  def log(message: String): Unit

enum Access:  case Open, Subscription, Unauthorised
enum Fetch:   case Found(file: FetchedFile); case Miss(why: String); case Refused(why: String)

trait SourceFetcher:
  def access: Access
  def fetch(work: WorkKey): Fetch

/** The reader is derived at the subclass's `extends` clause, so the provider keeps the public
  * no-argument constructor ServiceLoader needs. */
abstract class SourceFetcherPlugin[C](using reader: ConfigReader[C]) extends Plugin:
  type Config = C
  final def configReader: ConfigReader[C] = reader
  def create(config: C, host: HostServices): Either[String, SourceFetcher]
```

A plugin looks like this (`spike/plugins/hello/…/HelloPlugin.scala`):

```scala
import com.histmersion.plugin.PluginConfig.given          // lenient readers: env values are strings (§3.3)

final case class HelloConfig(greeting: String, maxItems: Int, mirrors: List[String], greeter: String)
    derives ConfigReader

final class HelloPlugin extends SourceFetcherPlugin[HelloConfig]:
  val info = PluginInfo("hello", PluginKind.SourceFetcher, "0.1.0", Spi.Version)
  def create(config: HelloConfig, host: HostServices) = …
```

and registers itself in `META-INF/services/com.histmersion.plugin.SourceFetcherPlugin`.

The host decodes through a path-dependent type, so the decoded value cannot reach the wrong plugin
and no cast is needed (`spike/plugins/host/…/PluginHost.scala`):

```scala
def decode(p: SourceFetcherPlugin[?], conf: Config): Either[String, p.Config] =
  p.configReader.from(conf.withoutPath("enabled").root).left.map(describe(p.info.id, _))
// … then p.create(typed, host)
```

### 2.3 Design rules

- **One ServiceLoader service** (`Plugin`, §2.2, decided 2026-10-18). Kinds are registries in the
  `PluginContext`, not service types; the host never filters plugins with `isInstanceOf`.
- **The provider is a class, not an `object`.** ServiceLoader needs a public no-argument
  constructor or a static `provider()` method, and a Scala `object` offers neither.
- **Descriptor before instance.** Instantiating a provider must be cheap and side-effect-free (the
  AGENTS.md rule for object initialisers). Real work starts in `create`, and only for enabled plugins.
- **The host lends services; the plugin owns nothing global.** HTTP goes through the host's client,
  so #1946's policy holds for third-party code too. So do logging and (for media) the store. A plugin
  that opens its own sockets cannot be stopped in-process: JDK 24 removed the Security Manager.
  Code review and signing (§9) are therefore the controls, not a sandbox (§12).
- **Version gate.** `spiVersion != Spi.Version` means the plugin is refused, with a message naming
  both versions. `PluginHost.check` also refuses duplicate ids and ids that are not kebab-case
  (tested).
- **Errors are values.** `create` returns `Either`, matching AGENTS.md's "expected failures are values".

## 3. The config contract

### 3.1 Where a plugin's settings live

```hocon
# ~/.config/histmersion/histmersion.conf
histmersion.plugins {
  hello {
    enabled  = true                         # the HOST's key; every plugin block has it
    greeting = "hello from the user's file"
    mirrors  = ["https://example.org/a", "https://example.org/b"]
  }
}
```

The effective block is built in four layers, highest first:

1. the user's `histmersion.plugins.<id>`;
2. the plugin's own defaults resource, `META-INF/histmersion/plugins/<id>.conf`, read through the
   plugin's class loader;
3. env substitutions (`max-items = ${?HISTMERSION_PLUGIN_HELLO_MAX_ITEMS}` in that resource),
   resolved against the environment **only**, never system properties (AGENTS.md);
4. `resolve()`.

The host then:

- reads and removes `enabled`;
- type-checks every key the user set against the defaults;
- decodes the rest with the plugin's reader.

### 3.2 Why defaults are a named resource, not `reference.conf`

Spike A put a `reference.conf` into both the host jar and the plugin jar. `ConfigFactory.parseResources("reference.conf")`
merged both on the JVM **and in the native image** (`from-host=true from-hello=true`). Merging
therefore works, but I recommend against relying on it:

- A plugin's `reference.conf` could silently override **host** keys.
- `ConfigLayers.check` would start accepting the plugin's keys as Histmersion settings.

A file named after the plugin id cannot collide with another plugin or with the host. The defaults
file also does a second job: it is the plugin's **type schema** (§3.3), so every key the plugin
reads must have a default in it.

### 3.3 Two gotchas, both hit in the spike

1. **The derived reader does not name the key.** `max-items = lots` decoded to
   `histmersion.plugins.test: Expected NUMBER but got STRING`, which names the block but not the
   key. This is the same limitation `ConfigLayers.typeCheck` works around for the host's own
   settings. The spike's `PluginHost.typeCheck` checks each key the user set against the kind of
   value in the plugin's defaults (resolved on their own, so `${?ENV}` lines keep their default).
   It reports `histmersion.plugins.hello.max-items must be a number, not "many"`, and it reports a
   key the defaults lack as an unknown setting (tested; shown in the native run).
2. **Env overrides arrive as strings.** With strict readers,
   `HISTMERSION_PLUGIN_HELLO_MAX_ITEMS=9` failed on the JVM with "Expected NUMBER but got STRING".
   The fix is the host's own lenient `Int/Long/Double/Boolean` givens, moved into the SPI as
   `com.histmersion.plugin.PluginConfig.given`, which a plugin imports where it derives. After the
   fix, `max-items=9` was read on both runtimes.

### 3.4 Host-side integration

- `ConfigLayers.check` must treat `histmersion.plugins` as an **open block**, as it already treats
  `agents.providers.claude-code.env`. Plugin keys are then checked by the plugin host, which knows
  the defaults, and not by `reference.conf`.
- A block no installed plugin claims, such as `helo` (a typo, or a plugin left out of this binary),
  is a warning, as in the spike. It is not an error: removing a plugin must not break startup.
- **Env naming:** `HISTMERSION_PLUGIN_<ID>_<KEY>`, upper snake case. Each plugin declares the
  variables itself in its defaults file. The host does not invent variables, because
  `ConfigLayers.envOverrides` only knows `reference.conf`.
- `/api/config` and `histmersion about` should list each plugin's id, version, kind, enabled flag
  and decoded config. Secrets need masking, so the SPI gains `def secretKeys: Set[String]`; the
  subscription plugins of §5 need it.
- **Config is read once, on the main thread.** That is the native-image rule from AGENTS.md:
  `systemEnvironment()` uses `StackValue`. Plugin decoding runs in `Main`'s composition root, right
  after `AppConfig.loadWithUserFile()`, and before `Deps` is built.

### 3.5 Config changes on the fly: no restart, no relink (user, 2026-10-18)

Only the **plugin set** (which jars are on the classpath) needs a relink (§7.3 compares its
checksum). A plugin already on the classpath whose jar did not change needs nothing native when its
config changes, and neither does enabling or disabling it: the image already holds it. A config
change should then take effect **in the running server**, without even a restart.

**Effect system.** The server has no Cats Effect: it is direct style — Ox structured concurrency
(`supervised`, `forkUser`, scoped resources via `useInScope`/`releaseAfterScope`), Tapir on
netty-sync, Magnum on plain JDBC. Cats Effect's `Resource` + `Hotswap` is exactly the right
*shape*, but adding CE for this one feature would mean a second runtime, `IO` at the plugin boundary
and more native-image surface. The same shape in Ox:

```scala
/** One generation = the plugins' registrations for one config, owning their resources in its own
  * Ox scope (clients, pools, caches are `useInScope` inside `register`). */
final class Generation(val config: Config, val registries: Registries, close: () => Unit, users: AtomicInteger)

/** The Hotswap analogue: readers take a snapshot; a reload builds the next generation off to the
  * side, publishes it atomically, then retires the old one once its last user lets go. */
final class PluginRuntime(load: Config => Either[String, Generation]):
  private val current = AtomicReference[Generation]
  def use[A](f: Registries => A): A =                     // an operation pins one generation
    val g = acquire(); try f(g.registries) finally release(g)
  def reload(next: Config): Either[String, ReloadReport] =
    load(next).map { g => val old = current.getAndSet(g); retireWhenIdle(old); report(old, g) }
```

- **Each plugin's `register` runs inside the generation's scope**, so whatever it allocates with
  `useInScope` is closed when that generation retires. This is `Resource` semantics without `IO`.
- **Callers pin a generation** (`runtime.use`): an import job or acquisition run started under the
  old config finishes with it; new work sees the new one. No lock on the hot path, just an
  `AtomicReference` and a use count.
- **Only what changed is rebuilt**: the diff is per `histmersion.plugins.<id>` block. An unchanged
  plugin's registrations, and their resources, are carried into the next generation as they are.
- **A failed reload changes nothing.** The new generation is built completely (decode, layered
  defaults, type checks, `register` into staged registries) before anything is published; on error
  the running one stays and the error is reported.
- **Triggers:** `histmersion config reload` (REST `POST /api/config/reload`, MCP), and optionally a
  `WatchService` on `~/.config/histmersion/histmersion.conf` with a short debounce.
- **Everything is hot, the database included** (user, 2026-10-18; see §3.6). The only thing a
  config change cannot do in place is change the server's listening port, and even that can be a
  second listener started before the old one stops.
- The same mechanism can later carry the host's own settings (today `AppConfig` is read once in
  `Main`), moving them one by one behind snapshots instead of process-wide values.

### 3.6 Hot components with dependencies: the database is swappable too (user, 2026-10-18)

**Today.** `object Db` keeps a process-wide cache of Hikari pools keyed by (url, user)
(`db/Db.scala`); `Main` builds `Deps` from it once; 74 files take a `DataSource` and keep it. A
change of database URL, credentials or pool size therefore means a restart. The rule from now on:
**no global or static JDBC** — a pool is a component like any other, owned by a scope, reachable
only through a handle.

**Ox has no `Hotswap`, so we add a small one**: a graph of hot components. Each node is built from
the config and the *current values of its dependencies*, inside its own Ox scope (so its resources
are `useInScope`/`releaseAfterScope` and close with the scope). Users hold a **stable handle**, never
the value, so a swap never needs them to reload.

```scala
/** A stable handle to a value that may be rebuilt. `use` pins the current generation for the call. */
trait Hot[+A]:
  def use[B](f: A => B): B

/** A node: how to build its value from config + its dependencies' current values, in a scope. */
final case class Node[A](name: String, deps: List[Hot[?]], keys: ConfigKeys)(
    build: (AppConfig, Deps) => Ox ?=> Either[String, A])

/** The graph. A config change → the set of nodes whose keys changed → those plus everything that
  * depends on them, rebuilt in topological order into a NEW generation off to the side; if every
  * node built, the generation is published atomically (one AtomicReference per node, swapped
  * together under one version number); the old nodes' scopes close when their last user lets go.
  * A failed build publishes nothing. */
final class HotGraph:
  def node[A](n: Node[A]): Hot[A]
  def reload(next: AppConfig): Either[String, ReloadReport]
```

For the database, the graph is roughly:

```
config ─► pool (Hikari, own scope) ─► migrations (Flyway, re-run on a new database) ─► repos
       └► query pool (read-only role)                                                 ├► jobs, managers, agents engine
                                                                                      └► plugin generation (§3.5)
```

- **Repos get a `Hot[DataSource]` (or a `DataSource` proxy that delegates each `getConnection` to the
  current pool).** The proxy is the cheapest migration of the 74 call sites: they keep their
  `DataSource` type, and a connection obtained before a swap stays on the old pool until it is closed;
  the old pool shuts down when its last connection comes back. Long-lived users (the jobs workers,
  the managers' tick, the agents engine) take a fresh connection per unit of work already, so they
  move to the new database at their next step.
- **Swapping a node re-runs the initialisation of everything that depends on it**: a new database
  gets its migrations, the query role's pool is rebuilt, the jobs/managers lease is re-acquired on
  the new database (the old lease expires on its own), and the plugin generation is rebuilt only if
  a plugin depends on the pool. Nodes that do not depend on the changed one are carried over as is.
- **In-flight work** pins its generation: an HTTP request, a job step, an agent step finish against
  the database they started on. A swap waits (bounded) for the old generation's users, then closes
  its scope; anything still running after the bound is reported, not killed silently.
- **Tests stop needing the global cache.** The reason for `Db`'s static cache is that many suites
  share one JVM; they move to one pool per test DB owned by a suite-level fixture scope.
- **The listening port** is the one process-level thing: a swap can start the new listener, move
  traffic, then stop the old one — or keep it restart-only if that is not worth it.
- **What it costs:** a `Hot` + `HotGraph` module (pure graph logic in core, scopes in server), the
  `DataSource` proxy, `Deps` built from the graph instead of from `Main`'s locals, and removing
  `object Db`'s cache. The config reload trigger is the same as §3.5's.

### 3.7 Secrets (user, 2026-10-18)

A plugin config (and the host's own) may hold credentials. A secret is never typed into the config
as a bare string field: its config type is `Secret`, and its value in HOCON is a **reference**:

| form | example | notes |
|---|---|---|
| `value:<literal>` | `password = "value:hunter2"` | discouraged; allowed; the reload report and `/api/config` warn about it |
| `env:<NAME>` | `password = "env:JSTOR_PASSWORD"` | read from the server's environment |
| `<vault>:<ref>` | `password = "keepassxc:Histmersion/JSTOR#password"` | resolved by a configured vault backend |

- **Vault backends are themselves an extension point** (a `secretVaults` registry in the plugin
  context), several can be configured at once, each with its own name: e.g. `keepassxc` (KeePassXC
  via `keepassxc-cli show -a <attr> <db> <entry>`, the database unlocked through its own prompt or
  key file — never a stored master password), `1password` (`op read op://vault/item/field`),
  `bitwarden` (`bw get <field> <id>` with a session token from `env:`), later the macOS keychain
  (`security find-generic-password -w`). The first-party ones ship built in and disabled.
- **Decoding:** `Secret` has a `ConfigReader` that parses the reference only; resolution happens when
  the plugin is registered (§3.5), through the context, so a missing or locked vault fails that
  plugin's registration with a clear error and leaves the others running.
- **Handling:** `Secret` has no `toString` value (prints `Secret(env:JSTOR_PASSWORD)` — the
  reference, never the value), is masked in every REST/MCP/CLI output, never written to the
  database, bundles, logs or the exported config, and is re-resolved on every reload (so a rotated
  password needs only `config reload`). Values are cached per generation, not process-wide.

## 4. Native image: the closed world

### 4.1 What GraalVM 25 does

- **ServiceLoader.** `native-image --expert-options-all` on 25.0.1 lists
  `-H:±UseServiceLoaderFeature  Automatically register services for run-time lookup using ServiceLoader. Default: + (enabled).`
  With it on, every `META-INF/services` provider on the image classpath is registered for
  `ServiceLoader` lookup. The spike's `HelloPlugin` had **no** reflection entry and was found and
  instantiated in the native image. Two more options let a build leave out a provider whose jar is
  present: `-H:ServiceLoaderFeatureExcludeServices=` and `-H:ServiceLoaderFeatureExcludeServiceProviders=`.
- **Per-jar metadata.** native-image scans every classpath entry for
  `META-INF/native-image/<groupId>/<artifactId>/reachability-metadata.json` (GraalVM manual,
  *Reachability Metadata*). The spike's plugin chose a class by name from config, which is invisible
  to static analysis. Results:
  - With the plugin jar's own metadata, the native binary instantiated `LoudGreeter`.
  - The same jar with the reflection entries removed failed at run time with
    `ClassNotFoundException: com.histmersion.spike.hello.LoudGreeter`.
  - Another variant with *all* the plugin's metadata removed also lost the defaults resource. The
    plugin's keys then read as unknown settings, because resources must be registered too.

  So **a plugin ships its own metadata**, produced by the tracing agent in its own build. The host
  build is not involved.
- **No class loading at run time.** A jar added after the build is not in the image. The spike's
  `without-hello` binary reported 0 plugins and warned that `histmersion.plugins.hello` configures
  no installed plugin, although its config was identical to the `with-hello` binary's.
- **Layers** (`-H:LayerCreate` / `-H:LayerUse`) exist in 25.0.1 and are marked "Experimental".
  A prebuilt base layer plus a thin plugin layer would make relinks fast. I did not spike this, and
  platform support is **(unverified)**. Revisit when it stops being experimental.

### 4.2 The options

| option | how | plus | minus |
|---|---|---|---|
| **A. Build-time selection** | config lists plugins → only their artefacts join `-cp` | smallest binary; a plugin not chosen is physically absent | every change of the set is a ~2 min / ~11 GB relink; first-party plugins need a resolver at build time |
| **B. Ship all first-party, enable at run time** | every first-party plugin on `-cp`; `histmersion.plugins.<id>.enabled` | toggling is a restart; one CI-tested image | binary grows with each plugin (small: they reuse sttp/circe already in the image); cannot cover third-party plugins |
| **C. JVM mode** | `java -cp kit/*:plugins/*` | plugins are discovered at startup, no relink | more RAM, slower start, a JDK at run time — a fallback, not the default |
| **D. Layers** | base layer + plugin layer | fast relinks | experimental (§4.1) |

**Recommendation: B for first-party plugins, A for third-party plugins, C as the fallback.**

- Every first-party plugin is in every image and enabled by config.
- First-party plugins can still be disabled one by one (`histmersion.plugins.<id>.enabled = false`).
- Third-party plugins live in the plugins directory and trigger a relink (§7).
- When the toolchain is missing or the relink fails, the kit runs in JVM mode.

## 5. The first kind: source fetching

### 5.1 Contract

`fetch(work: WorkKey): Fetch` is called in chain order until one answers `Found`.

`WorkKey` carries every identifier the caller has: DOI, arXiv id, PMCID, archive.org identifier,
ISBN, title, authors and year. A fetcher reads the ones it understands and answers `Miss` when none
fit.

The host owns everything else:

- **Order**, from config: `histmersion.acquire.chain = [unpaywall, core, openalex, pmc, arxiv, landing, pmc-text]`,
  defaulting to today's order.
- **The legal gate.** An `Access.Unauthorised` fetcher runs only when
  `histmersion.acquire.allow-unauthorised = true`, and never from `fetchLegal`. This is the watcher
  path in `research-process-managers.md` §4 (c).
- **The PDF check.** The `looksLikePdf` magic-byte test moves from `OaFetch` to the host, so that
  every plugin's file is checked.
- **The challenge rule.** A bot challenge becomes `Refused`, is logged, and is never retried.
- **Logging and the per-step trace** that ingest reports.

### 5.2 Shared lookups

Today `OaRecords` and `PmcLookup` share one Unpaywall/OpenAlex/Europe PMC answer across several
steps. Without sharing, splitting into plugins would ask the same API several times per work. The
host therefore keeps a per-fetch **memo** (`HostServices.memo(key)(compute)`), and the
Unpaywall-derived steps use the same key. Alternatively, the first-party OA steps stay **one**
plugin, `oa-index` (Unpaywall + OpenAlex + CORE + landing), and only the independent sources become
plugins of their own. I recommend the latter for phase 2: it is less churn and has the same effect
for users (open decision D4).

### 5.3 Migration order (first-party)

| order | plugin id | access | from | why this order |
|---|---|---|---|---|
| 1 | `archive-org` | Open | `ArchiveOrg` | independent, already its own client; fetches by identifier; the user asked for OA repositories first |
| 2 | `arxiv`, `pmc` (PDF + JATS) | Open | `OaFetch.arxivPdf`, `PmcLookup` | self-contained; PMC already has its own User-Agent rule |
| 3 | `oa-index` (Unpaywall, OpenAlex OA, CORE, repository landing, Zenodo, DataCite) | Open | rest of `OaFetch` | shares records (§5.2) |
| 4 | new OA repositories: DOAB/OAPEN (OA books), HAL, Persée, Gallica, HathiTrust full view, BHL | Open | new | each is a documented API with its own terms (§11); books matter most for this corpus |
| later | subscription TDM APIs (Elsevier, Wiley, Springer Nature) | Subscription | new | only with the user's own key and entitlement, within the provider's TDM terms (§11) |

## 6. Do the other providers move to the same SPI?

| provider family | verdict | kind | notes |
|---|---|---|---|
| Media collections (Met, Cleveland, AIC, LoC, Open Context; Wikidata/Commons, depicts, Pleiades) | **yes, second** | `MediaCollection` | `trait Collection` already is the shape (`search`, `matches`, `complete`, `conf`). The match logic (`MediaMatch`) and licence allowlist (`MediaLicence`) stay host-side, so a plugin **cannot** loosen the licence rule. The plugin returns `MediaRecord`s; the host judges, stores and credits them. `MediaImporter.of`'s `match` becomes a registry lookup |
| Event catalogues (GVP, NOAA, Wikidata) | **yes, third** | `EventCatalogue` | already pure URL/parse objects in `core/EventProviders`; the record (`CatalogRecord`) moves into the API |
| Gazetteer sources | **data plugins, not code** | — | `GazetteerLoad`'s `lpf` step already imports any Linked Places Format file a user drops into `places.sources.lpf-dir`, with title/licence from `<file>.meta.json`. Pinned extracts are built offline (`db/gazetteer/build.py`) and checked by sha256. A code SPI adds nothing until a gazetteer must be *queried live* |
| Period presets (PeriodO), Cliopatria, AWMC, ChronOntology | no | — | one-shot seed importers bound to the schema |
| Discovery/citations (OpenAlex, Crossref, Semantic Scholar) | later, maybe | `Discovery` | one implementation each today, and the CLI/MCP output shapes depend on them |
| OCR converter | no | — | a separate process with its own protocol |

## 7. User-added plugins

### 7.1 Where they live

- macOS: `~/Library/Application Support/Histmersion/plugins/`
- Linux: `$XDG_DATA_HOME/histmersion/plugins/`

Each plugin is one jar, `<id>-<version>.jar`, carrying:

- its provider in `META-INF/services/…`;
- its defaults in `META-INF/histmersion/plugins/<id>.conf`;
- its metadata in `META-INF/native-image/<group>/<artifact>/reachability-metadata.json`.

Dependencies beyond what the host ships are **shaded into the jar** (superseded: since §24 a plugin
declares them in its POM and they are installed beside it). The image has one class loader
and no isolation, so a second circe on the classpath is a conflict, not a choice. The relink refuses
a jar that duplicates a class already on the kit classpath. Config goes in `histmersion.conf` under
`histmersion.plugins.<id>`.

### 7.2 Commands (CLI)

```
histmersion plugin add <path|https-url|group:artifact:version> [--rebuild]
histmersion plugin list            # installed vs in-the-running-binary (digest compare)
histmersion plugin rm <id> [--rebuild]
histmersion plugin rebuild         # relink now
histmersion plugin status          # "rebuild needed" when the dir's digest ≠ the binary's
```

Notes on these commands:

- `add` accepts a local path or a URL first. Maven coordinates need a resolver, and the bundled JDK
  can run coursier's launcher jar for that. Coordinates come later (phase 4).
- These commands are host-side: they manage files and the LaunchAgent, like `histmersion calibre`.
  They never talk to the database, so the CLI stays on the right side of the CLI ↔ db boundary.
- The relink itself is a bundle script, `bin/histmersion-relink`, which the CLI runs.

### 7.3 Detecting a change

The relink writes `META-INF/histmersion/plugin-set.json` into a small generated jar on the image
classpath. It records `{digest, spi, plugins:[{file, sha256, id, version, kind}]}`, where `digest`
is `PluginHost.digest`: SHA-256 over the sorted `name sha256` lines, order-independent (tested).

The server reports it in `/api/about`. `histmersion plugin status` recomputes the digest over the
plugins directory and compares the two. A first-party-only image has a fixed digest of the empty set.

### 7.4 Relink and swap

1. **Lock.** One relink at a time (`<data>/plugins/.relink.lock`).
2. **Pre-flight in JVM mode, seconds instead of minutes.** Run
   `java -cp kit/*:plugins/* com.histmersion.server.PluginCheck` with the bundled JDK. It runs
   ServiceLoader discovery, the SPI version and duplicate-id checks, the class-duplication check,
   and parses the defaults and the user's blocks (`typeCheck` + `decode`). Any error stops here,
   before 11 GB of RAM are spent.
3. **Provenance** (§9): verify the signatures of the plugin jars, if configured.
4. **Sloth** over the plugin jars. A plugin compiled with Scala < 3.8 has the same `Unsafe` lazy vals
   as any other dependency. The kit's own jars are already patched.
5. **Memory check.** The real build peaked at 10.97 GB. With less than about 12 GB available, warn
   and offer JVM mode. Capping the heap with `-J-Xmx` trades memory for build time **(unverified)**.
6. **Build.** `native-image @kit/native-image.args -cp kit/*:plugin-set.jar:plugins/* … → bin/histmersion-server.new`.
7. **Self-check.** Run `histmersion-server.new --self-check`, a **new** flag: load config, decode
   plugins, print `plugin-set.json`, exit, and never touch the database. The server has no such
   mode today. During this research, running `histmersion-server --help` started a real server
   against the live database, which was harmless (§10.3), but a relink must never do that.
8. **Sign** with the setup identity (§9).
9. **Swap.**
   - `mv server server.prev; mv server.new server`.
   - macOS: `launchctl kickstart -k gui/$UID/com.histmersion.server`. Linux: `systemctl --user restart histmersion`.
   - Poll `/api/health` for up to 120 s, as `install-macos.sh` does, and check that `/api/about`'s
     plugin digest is the expected one.
   - **On failure:** move `server.prev` back, kickstart again and report the build log.
   - Keep one `.prev` for `histmersion plugin rollback`.

Plugin config changes alone (`enabled = false`) need only a config reload — no restart, no relink (§17).

## 8. Building the native image locally at setup

### 8.1 Toolchain

| need | macOS | Linux |
|---|---|---|
| C toolchain | Xcode Command Line Tools (`xcode-select --install`); GraalVM states "Native Image requires the Xcode command line tools", macOS 13+ and Xcode 14+ | `build-essential zlib1g-dev` (Debian/Ubuntu); `gcc glibc-devel zlib-devel libstdc++-static` (dnf); glibc ≥ 2.17, zlib ≥ 1.2.3, GCC ≥ 10.3 (GraalVM *Getting started on Linux*) |
| GraalVM | CE at the pin (25.0.1 when this was written; GraalVM CE 25.4, JDK 25.0.4.1.1, since 2026-10-23 — `scripts/bundle/graalvm-pins.txt`): a tarball from `graalvm/graalvm-ce-builds` with a pinned sha256, fetched by `curl` (no quarantine) into `~/Applications/Histmersion/graalvm` | same, `~/.local/share/histmersion/graalvm` |
| Sloth | only for the **from-source** build; the relink kit ships patched jars | same |
| sbt / Scala | only for the from-source build | same |

### 8.2 Licence

- **GraalVM CE** is "distributed under version 2 of the GNU General Public License with the
  'Classpath' Exception" (GraalVM FAQ). The native-image runtime that is linked into every
  executable, `substratevm/`, carries the same GPLv2 + Classpath Exception
  (`oracle/graal/substratevm/LICENSE`). The exception permits linking with independent modules
  under other terms, so the Histmersion binary is not GPL'd by being linked with it. Downloading
  CE in the installer, or bundling it, is redistribution under GPLv2 + CPE, which is permitted.
- **Oracle GraalVM** is under the GraalVM Free Terms and Conditions (GFTC). Redistribution is
  "permitted as long as it is not for a fee", and the GFTC permits use of "runtime image output
  produced by … Native Image … by any user" (GraalVM FAQ).
  - It adds features we do not use: G1 GC, PGO, obfuscation.
  - Oracle said in September 2025 that GraalVM would no longer be part of its Java SE products and
    would move off the Java release train (InfoWorld/ADTmag reports; Oracle's own wording
    **(unverified)**).
  - **Stay on CE.**
- **CE cadence.** Release notes list a 25.1 line with monthly feature releases, and one source
  mentions 25.2. The pin stays on the 25.0.x line, and `server.sh` already refuses a different major.

### 8.3 Measurements (spike B; commands in §10.2)

| step | wall | peak RSS | notes |
|---|---|---|---|
| `sbt server/compile` with the shared sbt-2 disk cache warm | 30 s | 1.2 GB | 97 % cache hits — **not** a cold compile |
| from-source cold build in an isolated home (`server/Runtime/fullClasspath; cli/Runtime/fullClasspath`, incl. Scala.js `fullLinkJS`, all downloads) | **2 min 4 s** (124 s wall; 345 s user CPU) | **4.55 GB** | every module compiled from source (core JVM + JS, db, web, server, cli) plus the Scala.js full link; dependencies downloaded into the isolated home: 306 MB coursier + 269 MB sbt boot = **575 MB**; sources + `target/` 230 MB. sbt still reported 30 disk-cache hits from a cache location I did not isolate, so read this as a lower bound |
| `sbt server/nativeImage`, cold Sloth | **3 min 56 s** (239 s wall; 838 s user CPU) | **10.97 GB** (`time -l` maximum resident set size 10 965 336 064 B; native-image's own "Peak RSS: 10,97GB") | 131 jars Sloth-patched and verified ≈ 2 min; one class (sttp `SafeGZIPInputStream`) restored to its original bytes by `SlothVerify` |
| — of which `native-image` | **1 min 41 s** | 10.97 GB | analysis 33.4 s, compile 24.3 s, layout 14.0 s, image 8.4 s; builder heap budget "15,52GB of memory (30,1% of system memory)"; CPU load 5.98 |
| result | — | — | **207.7 MB** binary (94.0 MB code, 111.6 MB heap); 124 251 compilation units; Mach-O arm64, `flags=0x20002(adhoc,linker-signed)` |
| spike A image (tiny host + plugin) | 14–16 s | 1.55 GB | for scale |

The relink without sbt is roughly the `native-image` row plus Sloth for the plugin jars only:
**about 2 minutes and 11 GB of RAM** on this machine. Machines with 16 GB should work. On machines
with 8 GB, assume JVM mode **(unverified; not measured)**.

Disk:

- GraalVM CE: 681 MB.
- Sloth-patched jars (the relink kit's `lib/`): 98 MB.
- The image: 208 MB, plus one `.prev` for rollback: 208 MB.
- The from-source build's `target/` in this worktree: 360 MB, with the sbt-2 disk cache holding the
  rest. The isolated cold build needed 575 MB of downloaded dependencies and 230 MB of sources and
  outputs.
- During the build, the volume lost about 460 MB net (df before/after), mostly the Sloth copies and
  the binary.

### 8.4 Signing and notarisation

- A binary that `ld` writes on this machine is ad-hoc signed by the linker, which Apple Silicon
  requires; the spike B binary shows `Signature=adhoc`, `flags=…(adhoc,linker-signed)`. It never
  carries `com.apple.quarantine`: `xattr -l` showed only `com.apple.provenance`. Gatekeeper
  assesses quarantined files, so it **never asks about a locally built binary, and notarisation is
  unnecessary**.
- `spctl --assess` "rejects" the self-signed binary (§10.3). That is irrelevant to execution,
  because nothing is quarantined.
- Today's prebuilt release bundle is quarantined when downloaded through a browser, and
  `install-macos.sh` strips the attribute with `xattr -dr`. A local build removes the need to do
  that.
- What remains is **TCC/keychain identity across rebuilds**, and that is a signing-identity
  question (§9).

### 8.5 Failure modes and fallback

| failure | detection | fallback |
|---|---|---|
| No Xcode CLT / gcc / zlib headers | pre-flight `xcrun --find clang`; `cc -lz` probe | print the one install command and stop, or use JVM mode |
| GraalVM download / sha mismatch | pinned sha256 | retry, or point at a local GraalVM (`GRAALVM_HOME`) |
| Not enough RAM (OOM, swap storm) | `sysctl hw.memsize` / `MemAvailable` before the build; builder exit code | JVM mode |
| native-image error (a plugin's missing metadata, an unsupported feature) | non-zero exit; the build log is kept | keep the current binary; report the plugin; JVM mode works with the same jars |
| Binary does not start | `--self-check`, then `/api/health` after the swap | roll back to `.prev` |

**JVM mode** runs `bin/histmersion-server-jvm`, which execs
`graalvm/bin/java -cp kit/lib/*:plugin-set.jar:plugins/* com.histmersion.server.Main`. The same
kit and the same plugins are discovered at run time, with no relink. The cost is the JVM's heap and
start-up, which I have not measured for the server. The LaunchAgent's `ProgramArguments` points at
whichever launcher is active.

### 8.6 Reproducibility and updates

- **Inputs are pinned:** GraalVM (version and sha256, `scripts/bundle/graalvm-pins.txt`), the kit's jars (sha256 in the kit manifest), the
  plugin jars (the plugin-set digest), and the native-image arguments. Output bytes will differ
  between machines and CPUs: native-image prints "Enable more CPU features with '-march=native'",
  and a local build may opt into that. What we can reproduce is the **inputs**, and those are
  recorded. `/api/about` should report the kit version and the plugin digest beside the commit.
- **Updates.** A new Histmersion version is a new kit. The update downloads it, or a from-source
  update builds it with `just bundle`, and then relinks with the user's plugins carried over.
  If the SPI version changed, pre-flight refuses each stale plugin by name before the build starts.
  The old binary stays until the new one passes its health check, as in §7.4.

### 8.7 What the current bundle becomes

| today | proposed |
|---|---|
| `scripts/bundle/server.sh` builds `bin/histmersion-server` + CLI with sbt (compile → Sloth → native-image) | it also writes `kit/`: `lib/*.jar` (the Sloth-patched classpath `slothPatch` already produces), `native-image.args` (the flags in `nativeImageSettings`), `kit.json` (versions, sha256s) |
| CI (`bundle.yml`) ships a prebuilt bundle per platform | CI ships **the kit** (+ Postgres + converter, unchanged) and keeps building and smoke-testing a binary from it; the prebuilt binary becomes optional for users without the toolchain |
| `install-macos.sh` copies, strips quarantine, signs if the identity exists | it provisions GraalVM, creates the signing identity (§9), runs `histmersion-relink` (no plugins = the stock image), then installs the LaunchAgent; on a toolchain failure it installs JVM mode |
| `build.sbt` `nativeImage`: `-cp` = Sloth-patched jars | unchanged, plus a `pluginJars` setting (the data dir's `plugins/*.jar` + the generated `plugin-set.jar`), so a developer's `sbt server/nativeImage` builds what the relink builds |
| Sloth | kept in the from-source build; the kit carries patched jars; the relink runs the Sloth assembly (shipped in the kit, run on the bundled JDK) over plugin jars only |

The CLI binary is unaffected: plugins are a server concern. It does not need a relink either,
because `plugin …` commands only manage files and run the relink script.

## 9. A signing identity at setup

The user suggested asking for a GPG key at setup, so that every locally built binary is "from the
same origin". **macOS code signing does not use GPG.** Gatekeeper, TCC (the privacy database behind
"Allow access to Documents") and keychain ACLs identify code by its **designated requirement**, which
comes from an X.509 code-signing identity in a keychain.

### 9.1 Options

| option | macOS identity across rebuilds | needs | verdict |
|---|---|---|---|
| (a) ad-hoc (`codesign -s -`, or the linker's own) | **none**: the DR is `cdhash H"…"`, which changes on every build, so TCC and keychain prompts reappear after each rebuild | nothing | today's fallback |
| (b) self-signed code-signing certificate, created at setup, in the user's login keychain | **stable**: DR = `identifier "com.histmersion.server" and certificate leaf = H"<sha1 of the cert>"` | `openssl` + `security` (both in macOS) | **recommended on macOS** |
| (c) the user's Apple Developer ID | stable (DR names the Team ID); notarisation possible | a paid Apple account | supported if present (`HISTMERSION_CODESIGN_IDENTITY`); unnecessary for local builds (§8.4) |
| (d) GPG | not recognised by macOS | gpg + gpg-agent | **recommended for Histmersion's own provenance** on both OSes |

### 9.2 Spike evidence for (b) (§10.3)

- The spike created a throwaway identity in a **temporary keychain** and used it to sign three
  separate builds: the two spike images and a copy of the real server.
- All three got the **same** DR:
  `identifier "com.histmersion.server" and certificate leaf = H"f2537a4e…dfe8"`.
- Before signing, the same binaries' ad-hoc DRs were `cdhash H"23047521…"` and `cdhash H"c0f5ef98…"`,
  one per build.
- `codesign` signed with the identity **while it was untrusted** (`CSSMERR_TP_NOT_TRUSTED`; "0 valid
  identities found"). The only requirement was that its keychain be in the search list.
- **Consequence:** setup does not need `security add-trusted-cert`, which is the step in today's
  `codesign-identity-macos.sh` that changes the user's trust settings and asks for the login
  password.
- **(verified 2026-10-23, §21.2)** TCC honours a grant for an untrusted self-signed DR as it does for
  a trusted one: it matches the stored requirement, a certificate hash. The trust step is therefore
  opt-in (`setup-macos --trust`, #3647).

### 9.3 Recommendation

- **macOS:** (b) at setup, automatically, with no question unless an identity named
  `HISTMERSION_CODESIGN_IDENTITY` already exists (then use it, which covers (c)). Every
  build and relink signs with it, as `install-macos.sh` already does for the bundle. If it is
  missing, sign ad-hoc and warn that macOS may ask for folder permissions again. Do not refuse:
  function is unaffected.
- **Provenance (both OSes):** (d), **opt-in at setup.** The question is "Sign Histmersion's builds
  and plugin set with a GPG key? [existing key id / generate one / no]". One key type cannot serve
  both roles here: macOS will not accept a GPG key, and an X.509 key in the keychain could sign
  data (`security cms -S`) but would need CMS verification code we do not have.
  - **What it signs:** `plugin-set.json` (§7.3), and a manifest of each built binary's sha256, each
    as a detached `.asc`.
  - **What verifies, and when:**
    - the relink, before it builds (the manifest of the plugins directory) and before it swaps (the
      new binary's hash);
    - the JVM-mode launcher, before `java` starts. That is the one mode in which plugins are loaded
      at run time.
  - **Third-party plugin jars** from Maven Central come with their author's `.asc`, because Central
    requires PGP signatures. `plugin add` verifies them and pins the signer's fingerprint per plugin
    id: `histmersion.plugins-trust.<id> = "<fingerprint>"`. That is trust on first use, with a clear
    prompt.
  - **Passphrases are never stored** in config or files. gpg-agent with pinentry-mac or the
    keychain holds them. Relinks are interactive (CLI), and nothing unattended signs.
  - **When the key is missing:** if provenance is configured and the key is unavailable, the relink
    **refuses** (the user asked for the guarantee) unless `--unsigned` is passed explicitly. If
    provenance is not configured, `plugin status` shows "unsigned", and nothing is silent.
- **Linux:** there is no OS code identity for ELF binaries and no TCC equivalent. (d) alone gives
  the "same origin" check, and setup offers it the same way.
- **Rotation.**
  - macOS: a new certificate means a new DR, so TCC asks once more. Do it only on expiry; the
    script makes 10-year certificates.
  - GPG: add the new fingerprint to `histmersion.plugins-trust.signers`, re-sign the current
    manifests, then remove the old fingerprint. Manifests name their signer.

## 10. Spikes

### 10.1 Spike A: SPI + plugin jar, JVM and native

Run it with `GRAALVM_HOME=~/.sdkman/candidates/java/25.0.1-graalce spike/plugins/run.sh`. The script:

- packages `hello`, writes the host's classpath and runs the host's 9 tests;
- runs the host on the JVM with and without the plugin jar;
- runs it on the JVM with an env override;
- builds three native images (`--no-fallback`) — with the plugin, without it, and with the plugin's
  reflection metadata removed — and runs them;
- runs the env-override and mistyped-value cases on the native binary.

The output, abridged:

```
[info] Passed: Total 9, Failed 0, Errors 0, Passed 9
== JVM, host only
[jvm] reference.conf: from-host=true from-hello=false
  [jvm] warning: histmersion.plugins.hello configures no installed plugin — ignored
[jvm] 0 source-fetcher plugin(s):
== JVM, host + hello
[jvm] reference.conf: from-host=true from-hello=true
  [jvm] warning: histmersion.plugins.helo configures no installed plugin — ignored
  [jvm] hello: HELLO FROM THE USER'S FILE! max-items=5 mirrors=https://example.org/a,https://example.org/b
[jvm] 1 source-fetcher plugin(s): hello 0.1.0 (enabled)
[jvm] hello access=Open fetch → Miss(hello has no copy of 10.1234/x (asked 2 mirrors))
== JVM, host + hello, env override                    (HISTMERSION_PLUGIN_HELLO_MAX_ITEMS=9)
  [jvm] hello: (hello from the plugin's defaults) max-items=9 mirrors=https://example.org/a
== native-image (with-hello)      Finished generating 'host-with-hello' in 13,7s.  Peak RSS: 1,56GB
[native] reference.conf: from-host=true from-hello=true
  [native] hello: HELLO FROM THE USER'S FILE! max-items=5 mirrors=https://example.org/a,https://example.org/b
[native] 1 source-fetcher plugin(s): hello 0.1.0 (enabled)
== native-image (without-hello)   Finished generating 'host-without-hello' in 16,1s.
  [native] warning: histmersion.plugins.hello configures no installed plugin — ignored
[native] 0 source-fetcher plugin(s):
== native-image (hello-no-reflection)
[native] plugin error: hello: greeter 'LoudGreeter': ClassNotFoundException: com.histmersion.spike.hello.LoudGreeter
== native (with-hello), env override
  [native] hello: (hello from the plugin's defaults) max-items=9 mirrors=https://example.org/a
== native (with-hello), a typo'd value                (max-items = "many")
[native] plugin error: histmersion.plugins.hello.max-items must be a number, not "many"
```

What it settles:

- **ServiceLoader in native image.** The provider was registered automatically, with no metadata
  entry of its own.
- **Typed config.** The plugin received its typed config. The user's file was layered over the
  plugin's defaults, and the env override worked once the lenient readers were added to the SPI.
- **Per-plugin metadata.** The plugin's own `reachability-metadata.json` decided whether reflection
  worked, and its resource entries decided whether its defaults were found.
- **The closed world.** A binary built without the jar does not see it.
- **The config gotchas of §3.3** — the error without a key name, and string env values — are real
  and fixed.

### 10.2 Spike B: the real server image

```bash
cd histmersion-wt/plugins-research        # fresh worktree, sbt-2 disk cache shared with other checkouts
export JAVA_HOME=~/.sdkman/candidates/java/25.0.1-graalce GRAALVM_HOME=$JAVA_HOME PATH=$JAVA_HOME/bin:$PATH
/usr/bin/time -l sbt --server -batch "server/compile"        # 30.39 real; max RSS 1 219 428 352
/usr/bin/time -l sbt --server -batch "server/nativeImage"    # 239.44 real, 837.84 user, 72.95 sys;
                                                             # max RSS 10 965 336 064; native-image 1m 41s
```

macOS `time -l` reports the maximum RSS of the whole process tree. It matches native-image's own
"Peak RSS: 10,97GB", so the builder JVM is the peak. The cold build used an isolated home, with
`HOME`, `-Duser.home`, `COURSIER_CACHE` and `sbt.global.base/boot/ivy/coursier.home` all under a
scratch directory, over a `git archive` of HEAD. Its numbers are in §8.3. The build outputs were
deleted after measuring.

### 10.3 Signing spike (temporary keychain only)

The spike used only a throwaway keychain and a short-lived certificate:

1. **Keychain:** `security create-keychain` made a throwaway keychain in the scratch directory.
2. **Certificate:** a 30-day self-signed certificate (`/usr/bin/openssl req -x509`, EKU
   `codeSigning`), imported into that keychain with `-T /usr/bin/codesign`.
3. **No prompts:** `security set-key-partition-list -S apple-tool:,apple:,codesign:` was applied to
   the temporary keychain only.
4. **Signing:** `codesign --sign <sha1> --keychain <tmp>` first answered "no identity found". The
   keychain had to be in the **search list**, so the user's list was saved, the temporary keychain
   appended for the signing, and the list restored. A diff against the saved list confirmed it was
   identical.
5. **Clean-up:** the keychain was then deleted with `security delete-keychain`.

The user's login keychain and existing keys were not touched or read.

```
build1 (host-with-hello), ad hoc:     designated => cdhash H"23047521d0a7729ab06cfd01431eca199ca0b3da"
build2 (host-without-hello), ad hoc:  designated => cdhash H"c0f5ef9811c8ca87505eb3670ab3d67a7d439bb0"
after signing all three (build1, build2, histmersion-server) with the spike identity:
  designated => identifier "com.histmersion.server" and certificate leaf = H"f2537a4efc8e84cbfc8785762e78f9a9c990dfe8"
  <each>: valid on disk / satisfies its Designated Requirement
codesign -dv --verbose=4 histmersion-server:
  Identifier=com.histmersion.server  Format=Mach-O thin (arm64)
  CodeDirectory v=20400 size=402767 flags=0x0(none) hashes=12581+2 location=embedded
  CDHash=56c6684e019c5626f919dc5d3a3065c852f84e0a  Authority=Histmersion Spike Local
  TeamIdentifier=not set  Internal requirements count=1 size=100
spctl --assess --type execute: rejected (origin=Histmersion Spike Local) — irrelevant: nothing is quarantined
```

One incident: I ran the signed server copy with `--help` to see whether it executes. The server has
no `--help`, so it **started**. It read `~/.config/histmersion/histmersion.conf` and opened a Hikari
pool to the live database before SIGPIPE ended it. I checked afterwards:

- The live schema is still 094, applied 2026-10-17. The worktree is at the live commit `e2e931a0`,
  so there were no new migrations to apply.
- No `jobs` row changed.
- The only row any table changed in the window was this task's own queue item, updated at 12:03,
  nine minutes before the incident.

This is why §7.4 adds `--self-check`.

## 11. Legal rules per provider kind

| kind | rule | enforcement in the design |
|---|---|---|
| **Open-access indexes and repositories** (Unpaywall, OpenAlex, CORE, Europe PMC/PMC, arXiv, Zenodo, DataCite, HAL, DOAB/OAPEN, Persée, Gallica) | documented APIs only, at the provider's pace; contact e-mail only in the parameter an API defines; the configured User-Agent; a bot challenge is no copy | `PluginHttp` = `HttpPolicy`; `Access.Open`; host-side PDF check |
| **archive.org** | public items only; **lending-only (`access-restricted`) items are refused**, as `import-archive` already does; no DRM circumvention | the fetcher answers `Refused("lending-only")`; the host never retries |
| **ResearchGate** | no public API, and its terms forbid automated access as the task states (the terms page answered HTTP 403 to my fetch, so I have not quoted it). Only a human route: the author's own copy, or a full-text request through the site by the user | **no fetcher plugin.** At most, an `acquire` queue item carrying the RG URL for the user. A third-party RG scraper is refused at review, and `Access` cannot describe it honestly |
| **JSTOR** | Terms §4 prohibit "any activity that automatically downloads or exports Content", including "web scraping, web harvesting, web data extraction". §3 allows "downloading or printing Content in reasonable amounts for non-commercial, scholarly purposes". The exceptions are Early Journal Content, Open JSTOR Collection Content and Open Artstor Content, under their own rights statements. TDM goes through JSTOR's own text-analysis service (fetched 2026-10-18) | **no subscription fetcher**, even with the user's credentials. Allowed: (i) a `jstor-open` fetcher for the openly licensed collections, only where their rights statement permits automated retrieval **(unverified per collection)**; (ii) a link-out: the user downloads by hand, then `histmersion ingest <file>` |
| **Subscription publishers with TDM APIs** (Elsevier, Wiley, Springer Nature) | only with the user's own API key or token and institutional entitlement, inside that provider's TDM terms (rate, non-commercial, no redistribution); the specific terms are **(unverified)** in this research | `Access.Subscription`; `secretKeys` masked; one plugin per provider, each citing the terms it implements |
| **Unauthorised sources** | not part of this repository and not named in it; a user's own plugin kept elsewhere must declare `Access.Unauthorised` | runs only with `allow-unauthorised = true`; never on `fetchLegal`; logged at WARN |
| **Media collections** | licence read from each record, judged by the host's allowlist; credit line kept; originals stay at the provider (docs/media-providers.md) | the licence judgement stays in the host, so a plugin cannot loosen it |
| **User plugins** | the user installs them and is responsible for the provider's terms; Histmersion's review applies only to first-party plugins | `plugin add` shows the plugin's `Access` and declared hosts before installing; an `Unauthorised` plugin needs the same config switch |

## 12. Risks

- **No sandbox.** A plugin runs in the server process with the database's credentials in reach.
  JDK 24 removed the Security Manager, and there is nothing to replace it in-process. Mitigations:
  the API offers no `DataSource`, plugins are signed or pinned (§9), and the relink states the
  hosts a plugin declares. Process isolation (a plugin as a sidecar over HTTP) is the only real
  sandbox, and it would cost the simplicity of this design.
- **Binary compatibility.** Plugins must be compiled with Scala ≤ the host's 3.8 and against the
  same SPI version. Dependency conflicts surface only at relink, since there is one class loader.
  Pre-flight catches duplicates but not semantic version skew of a shaded library.
- **Plugin metadata quality.** A plugin built without the tracing agent links cleanly and then fails
  at run time, as `hello-no-reflection` did. Pre-flight in JVM mode cannot detect this. The
  `--self-check` run catches only what start-up exercises.
- **11 GB per relink.** The build may not fit on small machines, which then fall back to JVM mode.
- **GraalVM's future.** Oracle moved GraalVM off the Java SE track in 2025. CE 25.0.x remains
  available; plan for the eventual move to a later CE line or Leyden-style AOT caches **(unverified)**.
- **Local builds diverge.** Every user's binary is different, which complicates bug reports.
  `/api/about` must report kit version + plugin digest + GraalVM version.
- **TCC with an untrusted self-signed identity** (§9.2): verified on 2026-10-23 (§21.2); the trust step is opt-in. Was: keep the trust step until it is
  checked.

## 13. Phased implementation plan

1. **Phase 1, the SPI and config (no behaviour change).**
   - Add `modules/plugin-api`: `Plugin`, the kinds, `PluginConfig` givens, `HostServices`.
   - Add `server/plugins/PluginHost`: discovery, `block`, `typeCheck`, `decode`, `check`, `digest`,
     with the spike's tests moved over.
   - Make `ConfigLayers.check` treat `histmersion.plugins` as an open block.
   - Report plugins in `/api/about` and `/api/config`, and add `--self-check` to the server.
2. **Phase 2, source fetchers.**
   - Add the `sourceFetchers` registry to `PluginContext` and a `WorkKey` with every identifier.
   - Move the host-side chain runner, legal gate, PDF check and memo into the host.
   - Port `archive-org`, `arxiv`, `pmc` and `oa-index` as first-party plugins in the same image (B),
     keeping today's order.
   - Remove the unauthorised fallback from this repository entirely (code, config keys, web UI,
     skills, docs, tests, native-image metadata); a user who wants it keeps it as a private plugin
     outside the repository.
   - Golden tests: the fixture-driven `OaFetch` tests must pass unchanged through the chain.
3. **Phase 3, the relink kit and JVM mode.**
   - `server.sh` writes `kit/`, and `bin/histmersion-relink` does pre-flight → Sloth → native-image
     → self-check → sign → swap → health → rollback.
   - Add `bin/histmersion-server-jvm`, and the CLI commands `plugin add (path|url) | list | rm |
     rebuild | status | rollback`.
   - `smoke.sh` gains a relink with a test plugin.
4. **Phase 4, local build at setup.**
   - The installer provisions GraalVM CE (pinned sha256), checks the toolchain, relinks, and falls
     back to JVM mode.
   - CI publishes kits, so the prebuilt binary becomes optional.
   - Add `plugin add group:artifact:version` (coursier on the bundled JDK) with `.asc` verification.
5. **Phase 5, signing.**
   - Setup creates the macOS identity and, opt-in, a GPG key or uses an existing one.
   - Sign and verify the plugin-set and binary manifests.
   - Verify TCC persistence with an untrusted identity, and drop `add-trusted-cert` if it holds. (Done: it holds — §21.2; `--trust` is opt-in, #3647.)
6. **Phase 6, more kinds.** `MediaCollection` (from `media/Collection`), then `EventCatalogue`.
   Then the new OA repositories of §5.3 (row 4), one plugin per provider, each citing its terms.

## 14. Decisions (user, 2026-10-18)

- **D1 — module:** `plugin-api` is its own module. It holds the interfaces plugins compile against
  and nothing else; plugins never see implementations (no `core`/`db`/`server` on their classpath).
- **D2 — first-party plugins:** built into the image and each one can be disabled by config.
- **D3 — unauthorised sources:** may exist as plugins, but they do not live in, and are not
  mentioned anywhere in, the Histmersion repository (it is to be open-sourced). Today's built-in
  fallback is removed from the repository once the acquisition chain runs through plugins (§13
  phase 2). Note: the git history still contains it; open-sourcing needs either a history rewrite
  or a fresh public repository.
- **D4 — open-access indexes: decided (user, 2026-10-18) — each open-access index is its own plugin.**
  Phase 2's one `oa-index` plugin (§16) became `oa-unpaywall`, `oa-core`, `oa-openalex` and `oa-landing`,
  each with its own enable switch and only the settings it uses; a config written for `oa-index` is
  still honoured, with a deprecation warning. As built: §20.2.
- **D5 — local build at setup: decided (user, 2026-10-18) — building the native image locally is the
  default.** The installers link the server on the machine from the kit; `--prebuilt` (or
  `HISTMERSION_BUILD=prebuilt`) opts out, `--jvm` stays. A machine that cannot build (memory, toolchain,
  the GraalVM download or its checksum, native-image, the self-check) falls back to the bundle's prebuilt
  binary — never JVM mode — and the install still succeeds. As built: §20.1.
- **D6 — signing:** optional.
- **D7 — paid sources:** plugins with credentials are allowed. Credentials are passed as secret
  references (§3.7). Each plugin still implements only what its provider's terms permit (§11).
- **D8 — installing a plugin:** Maven coordinates are one way among several: drop the jar into the
  plugins directory; `plugin add <path>` (copied in); `plugin add <url>` (downloaded, checksum
  shown, optionally verified); `plugin add <group:artifact:version>` (resolved from Maven).
- **One service type** and the app context (§2.2), **in-place config reload** (§3.5) and the **hot
  component graph** with no global JDBC (§3.6) were decided the same day.

## 15. As built (phase 1, 2026-10-18)

Phase 1 of #2018: the SPI, the config contract, secrets and the built-in vaults. Nothing that existed
before changed behaviour; acquisition, media and events do not go through plugins yet.

**Modules.**

| module | holds | depends on |
|---|---|---|
| `modules/plugin-api` (`com.histmersion.plugin`) | `Plugin`, `PluginInfo`, `Spi.Version` (= 1), `PluginContext`, `Registry[A]`, `PluginHttp`, `PluginLog`, `Secret` / `SecretRef`, `SecretVault`, `PluginConfig.given` (lenient readers) | sconfig + kindlings-sconfig-derivation only (D1) |
| `modules/plugin-vaults` (`com.histmersion.plugins.vaults`) | the `keepassxc`, `1password` and `bitwarden` plugins, `Subprocess` | plugin-api only — compiled as a third-party plugin would be |
| `server/plugins` | `PluginHost` (discovery, layering, type check, decode, secrets, staged registration), `Plugins` (the composition root's wiring), `Registries`, `Generation` | core, plugin-api; plugin-vaults on the class path |
| `core` | `PluginStatus` / `PluginsReport` / `PluginsMarkdown` (the report on every surface), `EnvOverrides`, the open `histmersion.plugins` block | — |

**The SPI as built (differences from §2.2's sketch).**

- `given configReader: ConfigReader[Config] = deferred`. Scala 3.8 rejects an abstract given, and a
  deferred one is filled in by the compiler: a plugin whose `Config` `derives ConfigReader` writes
  nothing (`type Config = KeePassXcConfig` is enough).
- Plugin ids are `[a-z0-9][a-z0-9-]*` (a leading digit is allowed for `1password`; HOCON reads
  `histmersion.plugins.1password` as a plain key, tested). `order` is reserved: it is the host's key.
- `PluginContext` has `http`, `log` (`info`/`warn`, the plugin's id on every line), `env(name)` (the
  environment read once on the main thread — for `PATH`, not for credentials), `secret(s)` and the one
  registry of phase 1, `secretVaults`. **The other extension points are not declared yet**:
  `sourceFetchers`, `mediaCollections` and `eventCatalogues` each need record types (`WorkKey`/`Fetch`,
  `MediaRecord`, `CatalogRecord`) that are not trivially small and belong to the phase that moves
  that part of the app. Adding a registry is compatible (§2.2), so `Spi.Version` stays 1 when they come.
- §3.4's `def secretKeys: Set[String]` is not needed: secrets are typed. The host finds every `Secret`
  in the decoded config by walking it (`productIterator`, options, collections, maps — generated code,
  no reflection), and the reported settings are the HOCON block, where a secret is only ever its
  reference.

**Secrets (§3.7).** `Secret` is a reference, `value:<literal>` | `env:<NAME>` | `<vault>:<ref>`
(vault names `[a-z0-9][a-z0-9-]*`, `env` and `value` reserved). Its reader parses the reference only,
and its errors never echo the text. `toString` is `Secret(env:NAME)`, a literal `Secret(value:***)`.
The host resolves every secret of an enabled plugin before `register`: `value:` is used and warned
about, `env:` reads the environment, `<vault>:<ref>` asks the vault registered under that name. The
values are cached for that generation only (a rebuild resolves them again). Reports show the
reference; a literal is shown as `value:***`, inside lists and objects too.

**The host.** `PluginHost.build(plugins, user file, env, services): Generation` is a pure function of
its inputs — no global state, no static cache — which is what a later reload (§3.5) needs: build the
next generation beside the running one, swap it when complete.

- Refused (reported, never loaded): a foreign `spiVersion`, an id claimed twice (all claimants), an id
  that is no config key, the reserved `order`. A provider that cannot be instantiated is a warning.
- Order: `histmersion.plugins.order`, then by id; an `order` entry that is no plugin is a warning, and
  so is a block no plugin claims.
- Effective block: **the env overrides the plugin's defaults declare > the user's block > the
  defaults**. The spike laid the user's block over the env-resolved defaults, so a file value beat
  the environment; `EnvOverrides.layer` (the marker technique `ConfigLayers` already used for
  `reference.conf`, now generic) restores the precedence the host's own settings have.
- Type check by key against the defaults resolved on their own (a `null` default — an optional value
  such as an optional `Secret` — takes any value and is left to the reader). A **disabled** plugin's
  block is type-checked but not decoded, so a half-written block of a disabled plugin is not an error.
- A plugin whose secrets name a vault that no plugin has registered **yet** is set aside and retried
  after the others: a vault plugin may sort after the plugins that use it (bitwarden's session can
  live in keepassxc). One still waiting when a pass registers nothing more fails, naming the vault.
- Registration is staged: what a plugin adds is committed only when `register` returned `Right`; a
  `Left` or an exception discards it. An `add` after `register` returned (a plugin that kept the
  context) is refused. A vault must be registered under its own `name`.
- A failure is the plugin's alone; the server still starts and reports it.

**Surfaces.** `/api/about` (`plugins`), `/api/config` (`plugins`, filled in by the server like
`writeKeyRequired`) and `histmersion about` (a summary line per plugin). `histmersion.plugins` is an open
block for `ConfigLayers.check`; `reference.conf` documents it and its `order`.

**The server's command line.** `histmersion-server` takes no argument (serve), `--self-check` or
`--help`/`-h`; anything else exits 1 with the usage **before** the config, the bundled runtime or the
database is touched (§10.3's incident). `--self-check` loads the config and the plugins, resolves the
secrets, prints `PluginsMarkdown.selfCheck` and exits 0 (all good), 2 (a plugin failed or was refused)
or 3 (the config is broken) — no database connection, no bundled runtime, no listener.

**The vaults.** Each runs its CLI as a subprocess: an argument vector, never a shell; the bare name
looked up on the server's `PATH` (or a full path in `cli`); a timeout (`timeout-seconds`, default 10)
after which the process and its descendants are killed; the value read from stdout and never put into
an error, which quotes the tool's stderr. An argument starting with `-` is refused (it would read as an
option). No master password is stored:

- `keepassxc` — `keepassxc-cli show -q -a <attr> [-k <key-file>] [--no-password] <database> <entry>`;
  `password` (a `Secret`, e.g. `env:`) is written to the tool's stdin; without one `--no-password` is
  passed, and a database that needs a password reports locked.
  **YubiKey (2026-10-19).** `yubikey` = `"<slot>[:<serial>]"` or a list of them (`["2:12345678",
  "2:87654321", "1"]`; a string may also separate several with commas), default `null`: each attempt
  adds `-y <slot>[:<serial>]` beside the password and/or key file (no password still means
  `--no-password`, so a YubiKey-only database opens). Malformed entries fail registration. If `ykman`
  is found (`ykman = "ykman"` by default; a bare name missing from the PATH just turns detection off, a
  wrong full path is an error, `""` = off), `ykman list --serials` runs first and only the connected
  keys are tried: an entry with a serial when that serial is listed, one without when any key is; none
  connected fails at once without running `keepassxc-cli`. Without detection every entry is tried in
  order. A KeePassXC message saying the key is absent ("Could not find hardware key…") or the
  challenge-response failed (slot not configured, USB error) moves to the next entry; invalid
  credentials, a missing entry, KeePassXC's own touch time-out, an unknown error or our per-attempt
  time-out (`yubikey-timeout-seconds`, 30) stop at once — another key cannot fix a wrong password, and
  an unanswered touch would only wait again. Each attempt logs "touch your YubiKey" at info. The error
  names every entry: tried and why, or not connected. The entry that opened the database last is tried
  first next time. `keepassxc-cli` 2.7 has no way to list keys, so detection is `ykman` only. Tests
  run fake `keepassxc-cli` and `ykman` scripts; `smoke.sh` checks a two-key list natively.
  **One unlock per generation (2026-10-26).** A start with four `keepassxc:` references asked for four
  touches: the host resolved each reference alone. `SecretVault.resolveAll(refs)` (a defaulted method,
  so SPI 2 plugins compiled before it keep working: the JVM calls the trait's default, which resolves
  one by one) is now what the host asks — once per vault and generation, on the first reference any
  plugin about to register needs, with every reference of that vault the generation's plugins hold;
  every answer, value or error, is kept for that build only (`PluginHost.GenerationSecrets`). The
  KeePassXC vault answers two or more with one `keepassxc-cli open -q [-k] [-y] [--no-password] <db>`
  (interactive mode, keepassxc-cli ≥ 2.6; `show` takes one entry, so there is no multi-entry `show`,
  and `export` would dump the whole database): the password line is written ALONE and the prompt
  (`<name>> `) awaited before any request — the unlock reads stdin ahead and swallows lines sent with
  it (seen with keepassxc-cli 2.8.0) — then one `show -q -a <attr> <entry>` per reference (spaces,
  tabs, quotes and backslashes escaped for the interactive line splitter), each answered before the
  next: the text up to the next prompt, without the readline echo of the request line. An empty answer
  is the entry's own error (stderr: "Could not find entry…"). The YubiKey plan, fallback and the touch
  log line apply to the `open` (one line per attempt). One reference, or an entry holding a control
  character, is still a plain `show`. A gated test (`HISTMERSION_TEST_KEEPASSXC=1`) runs the installed
  `keepassxc-cli` against a database it creates in a temporary directory.
- `1password` — `op read op://… [--account a]`; `service-account-token` (a `Secret`) becomes
  `OP_SERVICE_ACCOUNT_TOKEN` for `op` only; otherwise the desktop app signs `op` in.
- `bitwarden` — `bw get <field> <item> --nointeraction` with `BW_SESSION` from `session` (a `Secret`);
  without it the vault reports locked.

Tests run fake CLIs (shell scripts on a temporary `PATH`), never a real vault.

**Native image.** The plugin-api and plugin-vaults classes are on the server's image class path, so
the three plugins are in every binary (§4.2 option B). GraalVM registered the
`META-INF/services/com.histmersion.plugin.Plugin` providers by itself. Each plugin module carries its
own `META-INF/native-image/com.histmersion/<module>/reachability-metadata.json` (the defaults
resources and the lazy-val fields the tracing agent recorded during a `--self-check` with all three
vaults enabled against fake CLIs), as a third-party plugin jar would. `scripts/bundle/smoke.sh` proves
it on the built bundle: an unknown flag exits 1 with no Postgres started; `--self-check` with
keepassxc and bitwarden enabled against fake CLIs reports both registered, 1password disabled, the
session as its reference and never its value, exits 0, and exits 2 saying the database is locked when
the master password is wrong — and neither run starts the bundled runtime or a listener.

**Not in phase 1** (unchanged plan): moving acquisition/media/events to plugins (§13 phases 2 and 6),
the relink kit, `histmersion plugin add/list/…`, signing, `config reload` (§3.5) and the hot component
graph (§3.6). `PluginHost.digest` (§7.3) was moved over from the spike with its test, unused until the
relink kit. The spike under `spike/plugins/` stays as the evidence §10.1 quotes.

## 16. As built (phase 2, 2026-10-18)

Phase 2 of #2018 (source fetchers) and #2019 (the unauthorised fallback removed). Acquisition behaves as
before for every work: the same upstreams are asked in the same order with the same requests, and the
fixture suites of the chain (`OaFetchSuite`, `PmcFetchSuite`, `LandingFetchSuite`) pass unchanged
through it. The one removed behaviour is the fallback, which the live config never enabled.

**The SPI (plugin-api, still `Spi.Version` 1).** `PluginContext.sourceFetchers: Registry[SourceFetcher]`,
with a default that refuses every addition, so a context written for phase 1 (a test's) still compiles.
The record types, in `SourceFetcher.scala`:

| type | shape |
|---|---|
| `WorkKey` | `doi`, `arxiv`, `pmid`, `pmcid`, `archive` (an archive.org identifier), `isbn`, `title`, `authors`, `year`, and `format` (the file format the caller prefers when a work has several) |
| `Access` | `Open` · `Subscription` · `Unauthorised` — generic; no provider is named anywhere |
| `FetchedFile` | `source` (the provenance it is stored with), `url`, `bytes`, `extension`, and the optional `licence` (an SPDX id, `public-domain` or the licence's URL; ingest records it as the source's `license` when that has none, #2560 — added within SPI 1: the four-field `apply` and constructor remain, so older plugins build and link unchanged). The server's `acquire.FetchedFile` is an alias of it, so a plugin's copy is the one ingest stores |
| `Fetch` | `Found(file)` · `Miss(why)` · `Refused(why)` |
| `SourceFetcher` | `access`, `fetch(work, ctx): Fetch` — stateless across works; failures are values, an exception is read as a miss |
| `FetchContext` | `memo(key)(compute)` (one per fetch of one work, shared by every fetcher) and `log` |

**The host (`server/acquire/FetchChain`).** The chain over a generation's registered fetchers. It
implements the server's `OaFetcher` (`fetch`, `fetchLegal`, and `fetchWork(WorkKey, legalOnly)`, which
ingest now calls with the DOI, arXiv id, title, authors and year), and owns:

- **order:** `histmersion.acquire.chain` (comma-separated fetcher ids, like the landing lists, so an env
  override works; default `archive-org,unpaywall,core,openalex,pmc,arxiv,landing,pmc-text`), then every
  other registered fetcher in plugin registration order — a new plugin's fetcher runs with no config
  change. A listed id nothing registered (a disabled plugin) is skipped, noted once at startup, and the
  chain as it will run is logged (`acquire: chain archive-org → unpaywall → …`);
- **the legal gate** (`FetchChain.runs`, pure, tested): `Open` and `Subscription` always run;
  `Unauthorised` only with `histmersion.acquire.allow-unauthorised = true` (default `false`), never on the
  legal path — `fetchLegal` (a watcher's auto-ingest) and an archive.org import — and each run is logged at
  WARN. An unauthorised fetcher left off is noted at startup;
- **the PDF check:** a `Found` whose extension is `pdf` and whose bytes fail `looksLikePdf` (the magic
  bytes in the first KiB of a plausible size — moved here from `OaFetch`) is a miss; other formats (JATS
  `xml`, `epub`) are not PDF-checked;
- **the memo** (§5.2, decided here): per fetch, offered through `FetchContext.memo`, rather than kept as
  host-side lookups. The shared records are a plugin's own business (oa-index's Unpaywall/OpenAlex/DataCite
  records, pmc's Europe PMC hit and dataset record), the plugin knows when it needs them, and a third-party
  plugin with two steps gets the same tool; the host only scopes the values to one fetch. Keys are
  `<plugin id>:<what>:<identifier>`; the values are untyped, so a key reused with another type fails;
- **refusals** are logged and not asked again; when nothing is found the message is today's
  ("no legal open-access PDF found"), with each refusal appended (`(archive-org: … lending-only …)`);
- **misses** are logged too, at INFO, one line per fetcher that ran (`acquire: oapen: no copy — OAPEN
  Library: no record of this work (doi, title searched)`), so the log says which steps ran and why each
  found nothing. An ingest that found no copy adds a `chain` step listing every fetcher's answer
  (`FetchChain.Outcome`, through `OaFetcher.fetchWorkExplained`).

**The built-in plugins (`server/acquire/fetchers`).** *(D4 split `oa-index` into one plugin per index:
§20.2.)* Today's steps, each a plugin with its own
`enabled` (default `true`, `HISTMERSION_PLUGIN_<ID>_ENABLED`) and nothing else in its block:

| plugin | fetchers it registers | from |
|---|---|---|
| `archive-org` | `archive-org` — a public item's file by identifier (`WorkKey.format`, else EPUB, text PDF, PDF); a lending-only item is `Refused` | `ArchiveOrg` + `AcquireParse.pickArchiveFile` |
| `oa-index` | `unpaywall`, `core`, `openalex`, `landing` (Zenodo API, `citation_pdf_url`, DataCite) | `OaFetch`'s first three steps and its landing step |
| `pmc` | `pmc` (Europe PMC's free links, else the PMC Article Datasets PDF, MD5-checked), `pmc-text` (the JATS full text) | `OaFetch.PmcLookup` |
| `arxiv` | `arxiv` | `OaFetch.arxivPdf` |

A plugin registers several fetchers because its steps are not adjacent in today's order (the PMC PDF
before arXiv, its text after the landing pages; Unpaywall and OpenAlex before PMC, the landing pages
after arXiv). The step bodies are today's code, moved; they still try the candidates within a step in
the same order and log the same lines.

These four are **host code**, not plugin-api-only modules like the vaults: they read the host's
`histmersion.acquire` settings (endpoints, the contact e-mail, the landing rules, the CORE key) and core's
parsers (`AcquireParse`, `RepositoryLanding`, `Jats`), and the server's `Http` (the challenge rule, the
PMC User-Agent). Compiling them against plugin-api alone would have meant moving every `acquire.*`
endpoint key into four plugin blocks (new keys and env names for users) and core's parsers into a plugin
module, for no change in behaviour. So `Plugins.builtins` constructs them over the host's settings and
lazily built clients (registration opens nothing, so `--self-check` lists them) and hands them to
`PluginHost.build` beside the ServiceLoader-discovered plugins: the same ordering, enable switch, type
check (an unknown key in a built-in's block fails that plugin alone), staged registration and report.
Their defaults resources are in the server (`META-INF/histmersion/plugins/{archive-org,oa-index,pmc,arxiv}.conf`),
and their metadata (the resources, `Builtin`'s and `BuiltinConfig`'s lazy-val fields) is in the server's
`reachability-metadata.json`, recorded by the tracing agent during a `--self-check`. Moving them into a
plugin-api-only module stays possible later, once a plugin block may carry endpoint settings.

**Wiring.** `Plugins.load` builds ONE lazily created acquisition client (`acquire.tls` included) for the
generation; `Main.acquireHandlers` runs `FetchChain.of(generation.registries.sourceFetchers, …)`.
`OaFetch(cfg, http, openalex)` is now that same chain over the built-ins registered through
`PluginHost.build` with an empty user config — what the fixture suites and `AcquireLiveSuite` call, so
they exercise registration too. A server started without a generation (`Main.server`'s default, used by
tests) has an empty chain: every fetch is a miss, as offline. `archive import` checks the item (lending,
a file to take) as before, then downloads through the chain's legal path with `WorkKey(archive, format)`:
switching `archive-org` off switches the import's download off. It asks archive.org's metadata once more
than before (the fetcher checks the item itself).

**#2019.** The fallback's step, its config block and env variables, its config class, its test, its
native-image metadata entries, three of its mirror hosts on the landing deny list, and every mention in
the README, docs, skills (`plugin/` regenerated), the web UI's watcher texts and the pipeline/manager
descriptions are gone; `git grep -i` for its name and its common spellings returns nothing. No
replacement exists in the repository. The git history still contains it (§14 D3).

**Tests.** `SourceFetcherSuite` (plugin-api: `WorkKey.isEmpty`, the refusing registry, a phase-1
context), `FetchChainSuite` (order, the legal gate in all four combinations, the PDF check, first copy
wins, exceptions, refusals, the memo per fetch, `fetchWork`'s key, `of`'s notes), `BuiltinFetchersSuite`
(the four register without asking any upstream; today's chain; a plugin switched off by config or by
environment; an unknown key fails one plugin alone; the archive-org fetcher's miss, refusal and format
choice) and the unchanged fixture suites. `scripts/bundle/smoke.sh` checks that `--self-check` on the
native binary lists the four as registered with their fetchers, and one switched off as disabled.

**Not in phase 2:** fetchers for new repositories (§5.3 row 4), subscription fetchers, a per-step trace
in the ingest report, moving the built-ins out of the server.

## 17. As built (hot graph, 2026-10-18)

#2018's §3.5 and §3.6: the config is reloaded in the running server, the database included, with no
restart and no relink. There is no global or static JDBC any more.

**Pure, in core.** `HotPlan` orders the graph (Kahn's algorithm, declaration order among independent
nodes) and refuses a name used twice, a dependency on no node and a cycle (named: `a → c → b → a`);
`plan(nodes, changed)` is the nodes whose declared key prefixes a changed key matches (the key, one
under it, or a block above it) plus every dependent, in build order. `ConfigReload` diffs two merged
configs leaf by leaf (`histmersion { … }` only; a list is one leaf), splits the changes into applied and
restart-only, and builds the effective config: the new one with every restart-only key put back to its
running value, so what the server reports is what it runs. `LoadedConfig` keeps the merged settings for
this (`merged`). `ReloadReport` / `ReloadMarkdown` are the wire shape and its Markdown. Tests:
`HotPlanSuite`.

**The graph, in server (`server/hot`).**

- `Hot[A]` is the stable handle: `use(f)` runs `f` on the value of the generation the calling thread is
  pinned to, or pins the current generation for the call; `peek` reads without pinning; `latest` reads
  the latest published value.
- `HotGraph[C]` holds the nodes. `node(name, deps, keys, idle, onPublish)(build)` builds the first value
  at once. Each value is built **in an Ox `supervised` scope of its own, on a virtual thread that holds
  the scope open** until the value is retired; what the build acquires with `useInScope` /
  `releaseAfterScope` is released when that scope ends — or at once when the build returns `Left` or
  throws. `BuildCtx` gives the build the config, its dependencies' values in the generation being
  built, and on a rebuild its own previous value and config and the changed keys.
- `reload(next, changed)` rebuilds `HotPlan.plan`'s nodes in order into a new generation off to the
  side; if all built, it is published with one `AtomicReference` swap (a generation is an immutable
  snapshot: number, config, one instance per node), the `onPublish` hooks run **pinned to the new
  generation**, and the replaced instances are retired dependents first. If one failed, what the reload
  built is closed and nothing is published.
- **Pins.** A pin counts a user on every instance of one snapshot (count, then check that it is still
  the current snapshot — a reload that published meanwhile may already be draining it — else undo and
  retry). It is a thread-local; work handed to another thread carries it explicitly (`carry`, a
  `Carried` that is `run` or `drop`ped, releasing the pin either way). A retired instance is closed
  when no pin holds it and its own `idle` check holds (a pool: no connection out). A reload waits
  `config.drain-seconds` for that (not counting the reloading request's own pin) and then names what is
  still in use in the report and the log; a background drainer closes it when it is free. Nothing is
  closed under its user; at shutdown (`close`) what is still in use after the bound is closed with a
  warning.
- `Pinning` is the small interface the rest of the server sees (`pinned`, `carry`); `Pinning.none`
  stands for "no graph" (a test's fixed `Deps`).

Tests: `HotGraphSuite` — dependencies' values; a reload rebuilds the touched node and its dependents
and carries the rest (the same instance), closing the old dependents first; a key no node declares
publishes the config and rebuilds nothing; a failed build (and a build that throws) publishes nothing
and closes what the reload built; a duplicate name is refused; a pinned user keeps its generation and
the old values close only after it lets go (reported as draining at the bound); carried work runs on
its generation, a dropped carry releases it; an `idle` check holds a retired pool open; `onPublish`
runs on the new generation; a failed build's own resources are released at once.

**The server's nodes (`ServerGraph`, `Main`).**

```
config ─► pool ─► migrations ─► query-pool
                         └─────► leases
       ─► plugins ─► acquisition
       ─► watch
```

| node | keys | value | build |
|---|---|---|---|
| `pool` | `histmersion.db` | the application `HikariDataSource` | the bundle creates the database first (`Supervisor.reloadSource`); `Db.pool` in the node's scope; a connection is tried (no database → the build fails) |
| `migrations` | — | the schema version | Flyway on the new pool, the database-level settings, an eviction, the background `ANALYZE` (a daemon fork of the node's scope) |
| `query-pool` | `histmersion.query`, `histmersion.db` | `ReadOnlyPool(ds, own)` | the role provisioned on the new database, its own small pool; the loud fallback to the application pool as before |
| `leases` | — | lease held | on a swap, the agent lease claimed on the new database before publishing, released on the old one when its generation closes; once published (`onPublish`), the new database's interrupted jobs failed and — when the lease is held — its runs reconciled and queued ones enqueued (`AgentsHandlers.adoptDatabase`), as a start does |
| `plugins` | `histmersion.plugins`, `histmersion.acquire`, `histmersion.media.max-retry-after-seconds` | the plugin `Generation` | `Plugins.load(loaded, previous, changed)` — below |
| `acquisition` | `histmersion.acquire`, `histmersion.media.max-retry-after-seconds` | `AcquireClients` | one HTTP client (TLS roots included), the upstream clients, the fetch chain of the plugins' generation |
| `watch` | `histmersion.config` | watching | with `config.watch`, a `WatchService` on the config file's directory in a daemon fork of the node's scope; `watch-debounce-ms` after the last event it starts a reload on a thread of its own (never on the fork, which that reload may close) |

**No static pool.** `object Db`'s cache keyed by (url, user) is gone: `Db.pool` / `Db.queryPool` build a
new pool the caller owns; `Db.evict(ds)` and `Db.activeConnections(ds)` unwrap a pool from any
`DataSource` that wraps one. The repos keep their `DataSource` parameter: `HotDataSource` is a
`DataSource` whose every `getConnection` comes from the pool of the generation the calling thread is
pinned to (else the current one), itself taken pinned, so a pool cannot close between being chosen and
lending a connection. A connection taken before a swap stays on its pool, and that pool closes when its
last connection came back (the `idle` check). This was preferred to a `Hot[DataSource]` in all 74
signatures (every call site wrapped in `use` for nothing: the pin is per request or per job already) and
to a delegating `Connection` (more surface; Hikari's active count already says when a pool is free).

**`Deps`** is a class now: `cfg`, `configFile` and `plugins` are read from the graph at each call
(the pinned generation's, else the current one), `ds`/`queryDs` are the two proxies, `pins` carries work
to other threads, `reloader` is the reload. `Deps(cfg, ds[, queryDs])` builds a fixed one (tests).

**Pinned work.** Every HTTP request — REST, MCP, pages — is pinned by a tapir `RequestInterceptor`
(`PinRequests`; netty-sync runs the server logic on the request's thread). Carried to their threads: an
OCR job (queued with its submitter's pin, its failure recorded on the same generation), an agent run
(queued with its submitter's pin; the whole run is one unit — it finishes on the database it started
on), the parallel steps of a run, a background job (`BackgroundJob.start`), a backup, a merge apply, a
media job, the Retraction Watch refresh. The long-lived loops take a connection per unit of work and
move at their next one: the managers' tick (pinned per tick), the engine's resume/stall check (pinned
per pass), the lease renewal (unpinned, current database). Stopping a pool drops the pins of what was
still queued (the rows stay queued in their database).

**Config consumers.** Each handler reads `deps.cfg` where it uses it (`private def cfg`, never a `val`
captured at construction); classes that took a config value take it by name (`cfg: => AgentsConfig` in
the engine, the pipelines, the managers, the model picker, the plan pools and approvals; `BackupService`,
`HttpConverterClient`, `SourcePictures`, `JavaMediaHttp`'s request timeout, the toolbox caps). So the
agents' limits, the managers' settings (their tick interval included), media allowlists, quotas, the
backup settings, the converter URL and the API key (the auth handler is always in the pipeline and
reads the key per request) apply at once. The acquisition chain and its TLS trust are the
`acquisition` node.

**Restart-only** (`ConfigReload.RestartOnly`; kept at the running value, listed in the report):
`server.host`, `server.port`, `server.request-timeout-seconds`, `server.shutdown-grace-seconds`, `runtime.*`, `agents.enabled`,
`agents.max-concurrent-runs`, `agents.max-llm-calls-in-flight`, `converter.parallelism`. **The listening
port stays restart-only** rather than a second listener started before the old one stops: the port is
also in the LaunchAgent, `.mcp.json` and the public URL, which a reload cannot change, a second netty
bind would need the whole pipeline (auth, run tokens, pinning) rebuilt for a few seconds' gain, and a
LaunchAgent restart is cheap. The environment is the one read at startup (a native image reads it
through StackValue on the main thread): a reload re-reads the file only.

**Plugins: only what changed (§3.5).** `PluginHost.build` takes `carried`: plugins whose status and
contributions are taken over as they are, in their place in the order, without decoding, resolving or
registering them again. `Plugins.carried(previous, changed)` carries a plugin when its
`histmersion.plugins.<id>` block did not change and it was disabled, or registered with no secret in its
config. All are rebuilt when `plugins.order` changed, the whole block, a host key the built-ins read
(`acquire.*`), or the block of a plugin that registered a secret vault. A plugin holding a secret is
never carried (§3.7: a rotated password needs only a reload); a failed one is retried. `Generation`
records `contributions` (per plugin), `rebuilt` (the report's `plugins`) and `withSecrets`. Tests in
`PluginHostSuite`.

**Triggers.** `histmersion config reload` (exit 2 when an error left the config unchanged), `POST
/api/config/reload` (always 200; the report says whether anything was published), the MCP tool `config`
(`action: "reload"`; one tool more), and the watcher (`config.watch`, default off). A server started
without a config source (a test's) answers with an error that it has no config file to reload.

**Tests stop needing the global cache.** `DbSuite` (db test sources): each suite owns its pools —
`testPool(cfg)`, `testQueryPool(db, q)`, one per database and role, closed after the suite's own
`afterSuite` clean-up (`afterAll` is final there). The suites run one at a time, so a run holds one
suite's pools plus those of the servers it starts; `Main.server` now needs an Ox scope, and the
server's pools, job pool and agents close with it. `HotReloadSuite` (server, against two databases it
creates on the test cluster): an OCR job held in its conversion and a request in flight on database A
across a swap to B finish on A (the job's row and source are in A, not B); new requests and jobs go to B;
B was migrated; the reload reports the held generation as draining and the server holds no connection
to A once it drained. A file that does not parse, a database that is not there and a mistyped value
each change nothing. A changed `plugins.pmc.enabled` registers `pmc` alone again (rebuilding `plugins`
and `acquisition`), a hot key applies and `server.port` is reported restart-only.

**Not done.** The managers' and the engine's threads are not tested across a swap with agents on (a
test server never enables the agents — that would generate runtime trees and need models); they take a
connection per unit of work and are pinned per tick and per run, which the job and request tests
exercise through the same mechanism. A second listener for a changed port (above).

## 18. As built (kit: relink, JVM mode, user plugins, local build, signing; 2026-10-18)

Phases 3, 4 and 5 of #2018, with §14's decisions over the older text: signing is **optional** (D6);
a plugin is installed by dropping a jar in, `plugin add <path>`, `<url>` or `<group:artifact:version>`
(D8); first-party plugins stay built in and disablable (D2); only a changed plugin SET relinks — a
config change reloads in place (§17). D5 was not answered, so the prebuilt binary stays the default and
the local build is an installer option.

**The kit (§7.4, §8.7).** `server/relinkKit` (build.sbt) writes the Sloth-patched class path `nativeImage`
links — 133 jars, all jars (sbt 2 exports the modules as jars; a class directory or two jars of one name
fail the task) — with its order (`kit/classpath`), the native-image flags (`kit/native-image.args`,
`serverNativeOpts` (`--no-fallback` until #3752), now ONE list in build.sbt for both), the main class, and Sloth with
its verifier (`kit/tools/`). `scripts/bundle/server.sh` copies it into the bundle, adds
`kit/graalvm-version` (the pinned version: `.sdkmanrc` until 2026-10-23, `scripts/bundle/graalvm-pins.txt` since) and runs `PluginTool kit-manifest`: `kit/kit.json` (format,
version, commit, GraalVM version, plugin SPI, main class, every file's SHA-256) and `kit/SHA256SUMS` (the
same sums in the format `shasum -c` reads, which is what the relink checks — never with the kit's own
code). The server's reachability metadata is inside its jar, as every module's is. Beside the binary:
`bin/histmersion-server.plugin-set.json`, the set the installed binary was linked with (the empty set's
for the stock binary). Size: 105 MB.

**The plugin set (§7.3).** `PluginSet{digest, spiVersion, jars[{file, sha256, plugins[{id, version,
spiVersion, provides, access, error}]}], mode (native|jvm), kit, linkedAt}` (core). The digest is
`PluginKit.digest` (§7.3's, moved to core; `PluginHost.digest` delegates). A relink writes it into a small
generated jar on the image class path, `META-INF/histmersion/plugin-set.json`, and that jar carries its
own `META-INF/native-image/com.histmersion/plugin-set/reachability-metadata.json` registering the
resource — as a plugin jar would; the host build is not involved. `Plugins.load` reads it
(`PluginSetResource`), so `PluginsReport.set` reaches `/api/about`, `/api/config`, `histmersion about`
(`plugin set: <digest> · 1 jar (native): hello-0.1.0.jar`, or `first-party plugins only`) and
`--self-check` (`plugin-set digest: <sha256>` — what the relink compares).

**`PluginTool`** (server, a JVM main never reached from `Main`, so not in the image), run with the kit
on the class path:

- `describe <jar>…` — instantiates each provider loaded FROM that jar (code source checked), then
  registers it alone with its defaults (`enabled = true`, nothing else; the HTTP client refuses every
  request) into throw-away registries: id, version, SPI, what it registers, and the `Access` of each
  source fetcher. That is how `plugin add` shows "declared access" — the SPI has no static descriptor,
  and registering is the only truthful way to learn it. A plugin that needs settings to register
  says so (`error`); its access is then unknown.
- `check <kit> <jar>…` — refuses: a jar with no provider, a provider that cannot be loaded, a foreign
  SPI, an id twice (among the jars, or one the binary has: the kit's ServiceLoader plugins and the
  host's built-ins), and **a class twice** (in the kit's jars or an earlier plugin jar — one class loader,
  so a second copy is a conflict, §7.1).
- `plugin-set <out.jar> <record.json> <mode> <kit> <jar>…` and `kit-manifest`.

**`bin/histmersion-relink`** (bash 3.2, `scripts/bundle/relink.sh`) does §7.4 in this order: lock
(`<data>/plugins/.relink.lock`, a stale one taken over) → kit checksums (and no jar the manifest lacks) →
toolchain (GraalVM CE of exactly the kit's version with `native-image`; Xcode's clang / `cc`; memory ≥
`HISTMERSION_RELINK_MIN_MEMORY_GB`, default 12, else refused naming JVM mode, `--ignore-memory`; 1 GB of
disk) → GPG provenance when set up → **pre-flight on the JVM**: `PluginTool check`, then the server's own
`Main --self-check` over kit + plugin-set jar + plugins with the user's config, which must load (exit 3
refuses), report the expected digest and every new plugin registered — so a decode error stops the
relink in seconds, before 12 GB are spent → Sloth over copies of the plugin jars + `SlothVerify` →
`native-image @kit/native-image.args -cp <kit in order>:<plugin-set.jar>:<plugins> Main` into
`<install>/.relink/` (same file system as `bin/`, so the swap is a rename) → the **new binary's
`--self-check`** (same requirements: this is where a plugin whose jar lacks its own reachability metadata
fails, as spike A's `hello-no-reflection` did) → sign → swap (`bin/histmersion-server` and its records to
`.prev`) → restart (`launchctl kickstart -k`, `systemctl --user restart`; `HISTMERSION_SERVICE=none` and
`--no-restart` skip it) → `/api/health` within 120 s and `/api/about`'s plugin-set digest the expected
one, else the previous binary is put back and the service restarted again. Exit 0 / 1 usage / 2 refused
before building / 3 build or self-check failed, nothing changed / 4 rolled back. `--check` (pre-flight
only), `--rollback` (swaps `.prev` back; the replaced binary becomes `.prev`, so a second rollback
undoes the first), `--describe`, `--jvm`.

**JVM mode (§8.5).** `bin/histmersion-server-jvm` builds the plugin-set jar for this start (mode `jvm`),
verifies the plugins directory when GPG provenance is on, and execs `java -cp kit/lib/<in order>:
plugin-set.jar:plugins/*.jar com.histmersion.server.Main "$@"` with `HISTMERSION_HOME=<install>` and
`HISTMERSION_RUNTIME=true` — the process is `java`, so the bundle cannot be found from the executable's
location (`Supervisor.bundled`) — and `argv[0]` set to the launcher (`exec -a`), so `pgrep -f
<install>/bin/histmersion-server` still finds it. **Deviation:** §8.5 kept the LaunchAgent pointing at
"whichever launcher is active"; instead `histmersion-relink --jvm` swaps the launcher in AS
`bin/histmersion-server` (with `.prev`, health and rollback like a relink), so the LaunchAgent, the systemd
unit and the installers never change, and `plugin rebuild` returns to native the same way.
**Deviation:** there is no JDK in the bundle; "the bundled JDK" is the GraalVM CE the toolchain step
provisions (a full JDK), or any JDK of the pinned major via `HISTMERSION_JAVA_HOME`.

**`histmersion plugin …` (§7.2; the CLI, `cli/PluginCmd`).** `add (path|url|group:artifact:version)
[--yes] [--rebuild] [--verify] [--unsigned] [--sha256 h]`, `rm <id|file> [--rebuild]`, `list`, `status`,
`rebuild [--no-restart] [--unsigned] [--ignore-memory] [--jvm] [--check]`, `rollback [--no-restart]`.

- **Local only.** The CLI stays a pure HTTP client of the server for everything else; these commands
  act on THIS machine's install (`cli.plugins.install-dir`, else the install the CLI binary sits in —
  `~/bin/histmersion` resolved through its symlink — else the platform default) and data directory, and
  refuse with exit 1 when `cli.server-url` is not a loopback address. They never touch the database. The
  running server is only asked `/api/about` (status, list), when it answers.
- **add** stages the jar in `<data>/plugins/.staging/`, shows its SHA-256, verifies a signature when
  required, describes it through `histmersion-relink --describe`, refuses what `PluginKit.refusals` names
  (no plugin, foreign SPI, a broken provider) before asking — `y/N` on a terminal, `--yes` otherwise (no
  terminal and no `--yes` = not installed) — and installs it as `<id>-<version>.jar` (§7.1's name),
  replacing another version of the same plugin. `<data>/plugins/index.json` records where each jar came
  from, when, what it holds and its signer; a dropped-in jar has no entry and is still part of the set.
- **Maven (D8) without coursier** (superseded by §24, which resolves plugin POMs with coursier). §7.2
  planned coursier's launcher on the bundled JDK. A plugin shades
  every dependency the host does not ship (§7.1), so there is exactly one artifact to resolve: `PluginOps`
  fetches `<repo>/<group path>/<artifact>/<version>/<artifact>-<version>.jar` from each repository of
  `cli.plugins.maven-repos` in turn (default Maven Central) with curl, and checks it against the
  `.sha256` the repository publishes beside it, else the `.sha1` (Central always has one); no POM walk.
  `group::artifact:version` names the Scala 3 artifact (`artifact_3`). coursier would have added a JVM
  start and ~30 MB for the same single download, and transitive resolution a plugin must not need.
- **status** compares three digests (`PluginKit.status`): the directory's, the installed binary's record
  and the running server's — "up to date", "REBUILD NEEDED" (with the jars added, removed or changed since
  the link), "RESTART NEEDED" (relinked with `--no-restart`) or "UNKNOWN" (no record) — and prints the
  kit (version, GraalVM, SPI) and `histmersion-signing status`.

**Local build at setup (§8, phase 4).** *(The default and the fallback changed with D5: §20.1.)*
`scripts/bundle/install-build.sh`, sourced by both installers:
`--local-build` / `--jvm` / `HISTMERSION_BUILD=prebuilt|local|jvm`; **prebuilt stays the default** (D5
unanswered). `local` runs `histmersion-toolchain install` (the pinned GraalVM CE from
`graalvm/graalvm-ce-builds`'s release, the SHA-256 pinned in `scripts/bundle/graalvm-pins.txt` — the
release's own `.sha256` files and GitHub's asset digests agree — unpacked into
`<data>/toolchain/graalvm-ce-<version>`; 25.0.1 for macOS aarch64/x64 and Linux x64/aarch64, checked
2026-10-18; since 2026-10-23 GraalVM CE 25.4, JDK 25.0.4.1.1, for macOS aarch64 and Linux x64/aarch64 —
25.4 has no macOS x64 build — checked 2026-10-23), `histmersion-toolchain
check`, then `histmersion-relink --no-restart` before the service starts; any failure falls back to JVM
mode, and failing that keeps the prebuilt binary. **Deviation:** §8.1 put GraalVM under
`~/Applications/Histmersion/graalvm`; an install replaces that directory wholesale, so it lives in the
data directory, versioned by its pin. An install also relinks by itself when `<data>/plugins` holds jars
(§8.6: the user's plugins carried over an update; a stale SPI is refused there by name). The licence
(GPLv2 + Classpath Exception) is printed with the download and noted in the pins file.

**Signing (§9, phase 5, optional — D6).** `bin/histmersion-signing`:

- `setup-macos [--name N] [--trust]` creates the self-signed identity in the login keychain — only
  when the user runs it, after a y/N question (`codesign-identity-macos.sh`, with `--trust`/`--no-trust`
  and a keychain override for tests). Untrusted by default since #3647: the TCC check (§21.2) showed a
  grant survives a relink with an untrusted identity, so the trust step (`add-trusted-cert`, the login
  password) is opt-in. **Found here:** `codesign --sign "<name>"` looks among VALID identities only and
  answers "no identity found" for an untrusted one; the script signs with the identity's SHA-1 hash
  (as the spike did), and then an untrusted identity works. The relink and the installers sign through
  it (`codesign <file> <identifier>`); no identity → ad hoc, said once; `HISTMERSION_CODESIGN_IDENTITY=-`
  signs ad hoc without looking anything up (what the tests and the smoke run use).
- `use-gpg <key>` / `generate-gpg` / `off-gpg` — GPG provenance. Only the fingerprint is stored
  (`<data>/signing/gpg-key`); passphrases stay with gpg-agent/pinentry. With it on: `plugin add` needs a
  valid `<jar>.asc` (or `--unsigned`) and pins the signer per plugin id (`index.json`, trust on first use;
  §9.3 put the pin in `histmersion.conf`, but the CLI never writes the user's config); `add`/`rm` re-sign
  `<data>/plugins/plugins.manifest` (the jars' SHA-256s); the relink refuses a directory that does not
  match its signed manifest (a jar dropped in later is approved with `plugin add <that jar>`) unless
  `--unsigned`, and signs the new binary's SHA-256 (`bin/histmersion-server.sha256.asc`); the JVM launcher
  will not start on an unverified directory. A Maven author's key that is not in the keyring is named
  with the `gpg --recv-keys` to run — never fetched silently.

**Tests.** `PluginKitSuite` (core: digest, set round trip, the four status verdicts, coordinates and
origins, loopback, index and trust on first use, refusals, the kit manifest and `SHA256SUMS`),
`PluginsReportSuite` (the plugin-set lines), `PluginToolSuite` (server, against the REAL hello jar built
by sbt: describe, a foreign SPI, a jar without a provider, a broken provider, duplicate classes with
the kit and between plugins, the same id twice, the host's ids, the plugin-set jar found on a class path
with its metadata, the kit manifest), `PluginCmdSuite` (cli: locating the install, the remote refusal,
add from a path / a local HTTP server through the real curl / a fake Maven repository with SHA-1 checks
and a fallback repository, upgrade, signatures and the pinned signer, rm, list, status, rebuild).
`scripts/bundle/tests/relink-test.sh` (35 checks: the whole relink against fakes — success, every
refusal, native-image and self-check failures, a binary that does not come up and one that runs the
wrong set rolled back, the lock, memory, the kit's checksums, toolchain, provenance, `--jvm`,
rollback) and `kit-scripts-test.sh` (the JVM launcher, the toolchain against a fake GraalVM tarball on
file://, GPG provenance with the real gpg in a temporary GNUPGHOME, ad-hoc codesign, the installers' build
step; with `HISTMERSION_TEST_KEYCHAIN=1` a temporary keychain: an untrusted identity signs two different
binaries with one designated requirement, and the user's search list is restored and compared).

**The proof (`scripts/bundle/smoke.sh`).** On a copy of the bundle's `bin/`, `kit/`, `libexec/` and a
temporary data directory, through the native CLI: `plugin add <hello jar> --yes` (described: `hello
0.1.0 · SPI 1 · access: open · provides source-fetcher:hello`), `plugin status` (REBUILD NEEDED),
`plugin rebuild --no-restart` (a real native-image link), the relinked binary's `--self-check` with
`hello { enabled = true, greeter = LoudGreeter }` (registered — the greeter is found by name, so the
plugin jar's own metadata is in the image — and the directory's digest), `plugin status` (up to date),
`plugin list`, JVM mode's `--self-check` (registered, `(jvm)`, same digest), `plugin rollback` (the stock
binary, first-party only). Skipped with a note when no GraalVM of the kit's version is found or
`HISTMERSION_SMOKE_RELINK=0`.

**Measurements** (this machine, as §8.3; the smoke run of 2026-10-18 on the bundle of commit `c0c506d5`):

| step | wall | peak RSS | notes |
|---|---|---|---|
| `just bundle` (Postgres, converter with its models, both binaries, the kit) | 7 min 21 s | — | sbt disk cache warm; 1256 s user CPU |
| the kit | — | — | 133 jars + Sloth + manifests = 139 files, 105 MB |
| `plugin add <hello jar>` (copy, describe on the JVM) | ~1 s | — | |
| `plugin rebuild --no-restart` (the whole relink: checks, JVM pre-flight, Sloth, link, self-check, swap) | **108 s** | **12.43 GB** (`time -l`; native-image's own "Peak RSS: 12,43GB") | of which native-image 102 s |
| the relinked binary | — | — | 210 MB, the same size as the stock one; the hello plugin registered with `LoudGreeter` found by name |

The peak is 1.5 GB above spike B's 10.97 GB (the server grew since); `HISTMERSION_RELINK_MIN_MEMORY_GB`
defaults to 12, compared with the machine's TOTAL memory on macOS (it has no trustworthy "available"
figure) and with `MemAvailable` on Linux.

**Not done.** CI publishing kits as release artifacts (§13 phase 4's second bullet: `bundle.yml` is not
changed — a bundle already carries its kit); checking TCC persistence with an untrusted identity (needs
a real privacy prompt); GPG verification of the relinked binary at each start (the launcher verifies the
plugins directory; the native binary is checked at relink time); `-H:LayerCreate` relinks (still
experimental, §4.1). *(The first three: §21. `-H:LayerCreate` stays not done.)*

## 19. As built (providers, 2026-10-18)

Phase 6 of #2018, its first part: media collections and event catalogues are extension points, today's
providers are built-in plugins, and the web About window lists the plugins. Every media and events
import behaves as before: the fixture suites (`MediaImportJobSuite`, `MediaCollectionsJobSuite`,
`MediaPortalsJobSuite`, `MediaHttpSuite`, `MediaSourcesSuite`, `CollectionsSuite`, `PortalsSuite`,
`CommonsDepictionsSuite`, `WikidataCommonsSuite`, `EventImportSuite`) pass unchanged, now resolving their
provider through the registries. No migration.

**The SPI (plugin-api, still `Spi.Version` 1).** Two registries on `PluginContext`, each with a default
that refuses every addition (as `sourceFetchers` has), so older contexts and plugins still compile:

| type | shape |
|---|---|
| `MediaCollection` | `title`, `description`, `role` (`object` · `map` · `image`), `matchRule`, `perPlace` (3), `requestIntervalMs` (1000), `search(MediaPlace, PluginHttp)`, `complete(MediaItem, PluginHttp)` |
| `MediaPlace` | `id`, `label`, `wikidata`, `lat`, `lon`, `period` — the place on the map the host asks about |
| `MediaItem` | `key`, `title`, `pageUrl`, `imageUrl`, `rights: Either[String, MediaRights]`, `thumbUrl`, `originalUrl`, `geography` (most specific first), `period`, `ids` (gazetteer ids), `withheld` |
| `MediaRights` | `licence` AS THE PROVIDER STATES IT (an id, a short name or a CC URL), `licenceUrl`, `author`, `credit` |
| `MediaMatchRule` | `Museum` · `Map(maxYear)` · `PlaceIds(deeper)` — a choice among the host's rules, not a rule |
| `EventCatalogue` | `title`, `licence`, `citation`, `home`, `requestIntervalMs`, `floorYear` (`None`; added 2026-10-18, see below), `parts(CatalogueQuery)`, `read(part, query, PluginHttp): CataloguePage` |
| `CatalogueQuery` | `from`, `to`, `area` (`minLon, minLat, maxLon, maxLat`) |
| `CatalogueEvent` | the catalogue's id, url, label, kind, the four-point dating, position, historicity, VEI, magnitude, deaths, Wikidata item, names, description, group, stated year and ±, dating method, `raw` |
| `CataloguePage` | `events`, `next` (the following page of a paged part) |

**§11 holds: the judgement is the host's.** A plugin's collection runs through the host's own
`CollectionImport`, adapted by `server/media/PluginCollection`:

- the stated licence is classified by the host (`MediaLicence.classify`, `ofUrl` for a URL, the licence
  URL when the statement is unclear); unknown stays unknown and is never stored; a record without a
  credit line is refused;
- the allowlist is `media.collections.licences` (default `cc0,public-domain,cc-by,cc-by-sa`), replaced for
  one collection by `media.collections.per-collection.<name>` (an open block in `ConfigLayers.check`) —
  host settings, read at each import; nothing the plugin declares takes part. A provider-stated CC BY-NC
  record is refused until the user lists `cc-by-nc` for that collection (tested);
- the match is `MediaMatch` (`museum`, `map`, `withIds`) under the rule the collection chose: names only
  by `Geocode.key`, uncertain values ignored, the dates overlapping, the basis stored on the link;
- the host's `Polite` client of the run carries the plugin's requests (`PluginHttp`; the configured
  User-Agent replaces any the plugin sends — `Polite.get` gained a `headers` argument), and the host
  downloads, thumbnails, stores, credits and records the check;
- a plugin's exception or `null` is a failure of that call (`guard`), never the end of the import;
- a plugin's collection asks about places; it may not register under a host name (a built-in import, an
  alias of one, or a provider pictures are stored under — `BuiltinMedia.reserved`), nor with a role
  outside `MediaCollection.Roles` (the database allows no other on a link).

A plugin's catalogue is read by the host's `EventCatalogHandlers.runImport`: its parts in order, a part
read before skipped (as the built-ins' are; `--refresh` clears them), a paged part followed through
`next`; each record checked by `PluginEvents.record` (an id and label; a historicity of the host's; a
dating that does not run backwards; a position on the globe, both coordinates or neither; the kind
spelled by `EventTaxonomy.refine`; a Wikidata id that is no item dropped), then DATED by the host
(`PluginEvents.derive`, below), before the same years, `--bbox`, VEI/magnitude thresholds,
`CatalogMerge.place` and the store. A refused record and a failed part are lines of the report; the
import goes on. The card's catalogue line takes a plugin catalogue's title, licence and citation from the
registry (the store keeps only the provider id). A catalogue may not take a built-in's name or alias.

**A plugin's dating, at the import and at rederive (follow-up, 2026-10-18).** The host has no parser for
a plugin's `raw`, so it dates a plugin's records from the structured fields the SPI already carries, with
ONE pure function, `PluginEvents.derive(record, limits)`, which the import and `events rederive` both call:

- a record that gives none of the four points but a stated year (`central`, with `plusMinus`) is dated
  `central ± plusMinus` (`CatalogDating.year` → `CatalogDating.span`) — exactly how the host dates the
  GVP's and NOAA's stated years; a record that gives a span keeps it, and the stated year and ± are kept
  as stated in either case (the card's "250 CE ± 2500");
- every point is then held inside the limits (`CatalogDating.clamp`): the present for every catalogue,
  as before, and — new — the catalogue's own floor: `EventCatalogue.floorYear: Option[Long] = None`, the
  earliest year any of its events can be (as `events.gvp.floor-year` is the GVP's, −9700). One defaulted
  member, so `Spi.Version` stays 1 and a plugin built against the earlier SPI is unchanged; a
  `floorYear` that throws is no floor (`PluginEvents.limits`).

`derive` is idempotent: a stored record already has its span, and clamping a clamped span changes
nothing, so `events rederive` after an import changes no plugin record (tested). A plugin switched off
since is re-derived with the present alone: its records were already held at its floor, so the looser
limit changes nothing either. A record stored before this change that stated only a year ± is dated by
the next `events rederive` — the same result the import now gives. Positions are not clamped, for
plugins or built-ins: a plugin's position off the globe, or with one coordinate, is refused at the
check (as before). `EventRederiveReport.fromRaw` now counts only built-in records with a `raw` (a
plugin's `raw` is kept but never parsed). Tests: `ProviderPluginsSuite` (+2: `derive` — a stated year ±,
a given span kept, the present and the floor, nothing invented, idempotent; `limits` — a floor, a
throwing one, none), `ProviderSpiSuite` (`floorYear` defaults to `None`), `PluginCatalogueSuite` (+1, DB:
a catalogue with a floor whose records reach past the present, before the floor, and state only a year
± — stored held and dated at the import, the record and its event alike; `events rederive` then changes
none, also with the plugin switched off). No native-image metadata change: no new config, DTO or
reflective class.

**The built-in plugins.** Host code, as phase 2's fetchers (`server/media/BuiltinMedia`,
`server/events/BuiltinCatalogues`), handed to `PluginHost.build` by `Plugins.builtins` with the same
`Builtin` wrapper, ordering, enable switch, type check and report:

| plugin | registers | settings |
|---|---|---|
| `media-wikidata` · `media-depicts` · `media-met` · `media-cleveland` · `media-aic` · `media-loc` · `media-pleiades` · `media-opencontext` | `media-collection:wikidata` · `depicts` · `met` · `cleveland` · `aic` · `loc` · `pleiades` · `opencontext` | `media.<provider>.*`, unchanged |
| `media-smithsonian` · `media-europeana` (2026-10-26; **off by default**) | `media-collection:smithsonian` · `europeana` | `api-key` (a `Secret`) in the plugin block, resolved at registration (`BuiltinWith[KeyedConfig]`, as `oa-core`); the rest `media.smithsonian.*` / `media.europeana.*`. Without a key the collection is registered and listed ("NO KEY SET") and its import is refused naming the setting; the API is not asked |
| `events-gvp` · `events-noaa` · `events-wikidata` | `event-catalogue:gvp` · `noaa` · `wikidata` | `events.<catalogue>.*`, unchanged |

Plugin ids are prefixed (`media-`, `events-`) because the registry names collide across kinds
(`wikidata` is a media import and a catalogue) and one switch per provider was asked for; the registry
names are what `media import` and `events import` take, so the commands are unchanged.

Their entries are `HostMediaCollection` / `HostEventCatalogue`: SPI values whose `search`/`read` are never
called, which the host recognises and runs with today's code (`MediaImporter.of`, the GVP/NOAA/Wikidata
branches) over the settings of the calling thread's config. **Config keys are layered, not mapped:**
`histmersion.plugins.<id>.enabled` (default `true`, env `HISTMERSION_PLUGIN_<ID>_ENABLED`) decides
whether the provider is registered; `media.<provider>.enabled` is still read at the import, so both
must be on, and every other `media.*`/`events.*` key keeps its meaning and env name. Mapping the old
keys into plugin blocks would have renamed every documented key and env variable for no change in
behaviour; reading them at each import also keeps them hot without rebuilding a plugin.

**Resolution.** `MediaHandlers.providerOf` asks `MediaImporters.resolve` over
`deps.plugins.registries.mediaCollections` — the generation the request (or job) is pinned to; aliases
(`commons`, `cma`, `artic`, `oc`, the stored provider names…) resolve to the built-ins; an unknown name
lists the registered ones (built-ins in their old order, then plugins' in registration order) and a
switched-off built-in names its switch, both `400`. Events: `BuiltinCatalogues.resolve` the same way;
`events catalogues` lists the registry. `Deps(cfg, ds)` and `Main.server`'s default generation are now
`Plugins.offline` — the built-in providers and no fetcher (still offline) — which is how the golden suites
reach the registries without a config file.

**Surfaces.** `GET /api/media/providers` (`[MediaProviderInfo{name, title, over, description, plugin,
builtin, licences}]`, REST only like the import itself) and the registry-backed `GET /api/events/catalogues`.
The CLI's `media import` takes the provider as an argument (it was one subcommand per provider) and
`histmersion media providers` lists them, so a third-party plugin's collection appears with no CLI change;
the per-provider help text moved to that list. `PluginStatus.provides` names `media-collection:<name>`
and `event-catalogue:<name>`.

**Hot reload (§17).** The plugins node still rebuilds only the plugins whose block changed. New:
`Plugins.carried` keeps the media/events built-ins across a change of a host key (`acquire.*`,
`media.max-retry-after-seconds`) — they read no host key at registration and never the HTTP client one
shapes — while the fetchers and third-party plugins (whose `ctx.http` follows `acquire.tls`) are rebuilt
as before; `order`, the whole block or a vault's block still rebuild all. A `media.*`/`events.*` change
rebuilds no plugin. Long work pins its generation as before: a media job (`deps.pins.carry`) and an
events job (`BackgroundJob.start(pins = deps.pins)`) keep the collection or catalogue of the generation
they started on until they finish.

**Web About window.** A *Plugins* section (`AboutView.plugins`): one row per plugin — its state as the
health badge (registered ok, disabled grey, failed or refused down), its version, what it registered and
its settings (a secret only ever as its reference: the report never holds a value) — and the report's
warnings. Read once when the window opens, as the other sections; nothing cached, nothing to clean up.

**Native image.** The eleven defaults resources and the new config/DTO lazy-val fields were recorded
with the tracing agent (a `--self-check` with one built-in switched off, and the server and CLI over
`/api/media/providers`, `/api/events/catalogues` and `/api/about`).

**Tests.** `ProviderSpiSuite` (plugin-api: defaults, the refusing registries, SPI 1), `ProviderPluginsSuite`
(the built-ins registered and switched off by config or env; aliases and the unknown-name message; a
third-party collection and catalogue registered and listed after the built-ins; host names and foreign
roles refused; the reload carry rule; the host's reading of stated licences; a plugin item and a
catalogue record checked), `PluginMediaImportSuite` (DB: a third-party collection through `media import`
— a stated CC BY-NC record refused under the default allowlist, stored once allowed per collection,
all-rights-reserved never; the match basis on the link; the host's User-Agent on the plugin's request;
`/api/media/providers` and `/api/about` through the server), `PluginCatalogueSuite` (DB: a third-party
catalogue's parts, a followed page, a refused record and a failed part reported, a second import reading
only what was left, the card's terms), and `PlaceHoverSuite` (the About section).

**Not done.** Moving the built-ins into plugin-api-only modules (they read host settings and core's
parsers, as the fetchers do); a plugin collection that asks about events (the host's collection import
is for places); a parser hook so `events rederive` can re-read a plugin's `raw` (not needed for the
dating, which the host derives from the structured fields — above; it would only matter for a plugin
that changes how it reads its own records); new providers (§5.3 row 4, the media providers "not in this
slice").

## 20. As built (defaults: the local build, one plugin per OA index; 2026-10-18)

### 20.1 D5: the installers link the server on the machine by default

`scripts/bundle/install-build.sh` (sourced by `install-macos.sh` and `install-linux.sh`):

| mode | chosen by | does |
|---|---|---|
| `local` | **the default** (`--local-build` and `HISTMERSION_BUILD=local` still accepted) | `histmersion-toolchain preflight` → `histmersion-toolchain path`, else `install` → `HISTMERSION_SERVICE=none histmersion-relink --no-restart` |
| `prebuilt` | `--prebuilt`, `HISTMERSION_BUILD=prebuilt` | the bundle's binary as shipped; no toolchain. With plugin jars installed it says they are not in it |
| `jvm` | `--jvm`, `HISTMERSION_BUILD=jvm` | `histmersion-relink --jvm --no-restart` (unchanged) |

**The fallback is the prebuilt binary, never JVM mode.** Each failure is caught where it happens, and
the installer says why, says it is falling back, and goes on with the bundle's binary (`ib_result=prebuilt`,
which the macOS installer then signs like any prebuilt binary):

| failure | caught by | downloaded? linked? |
|---|---|---|
| memory under `HISTMERSION_RELINK_MIN_MEMORY_GB` (default 12), no C toolchain, too little disk (2 GB without GraalVM, 1 GB with) | `histmersion-toolchain preflight` (new: `check` without GraalVM) | neither — asked before the ~325 MB download |
| the GraalVM download, a missing pin, the SHA-256 | `histmersion-toolchain install` (exit 2) | not unpacked, no link |
| the kit's checksums, the plugins' pre-flight on the JVM, provenance | `histmersion-relink` exit 2 | no link |
| native-image, the new binary's `--self-check` | `histmersion-relink` exit 3 | the binary in `bin/` is untouched |

The fallback is always available because a relink swaps only a binary that passed its self-check: on
every failure the bundle's binary is still in `bin/`. With plugin jars installed, the message says the
binary does NOT hold them and names `histmersion plugin rebuild` (and `--jvm`). A JVM mode that cannot be
set up (no JDK) falls back the same way (it used to fail the install). The only way the build step fails
is a bundle without a server binary, which the installers refuse before copying anyway.

**Not linking twice (`bin/histmersion-server.build`).** A key=value record beside every binary linked on
a machine: `mode` (`native`/`jvm`), `kit` (the SHA-256 of `kit/SHA256SUMS`, so every kit file), `plugins`
(the SHA-256 of `"<sha256>  <jar>"` lines, sorted — computable in bash, unlike `PluginKit.digest`, which
needs a JVM), `machine` (the SHA-256 of `histmersion:` + the hardware UUID on macOS or the machine-id on
Linux — never the raw id), `origin` (`relink`/`bundle`), `built`. `histmersion-relink` writes it for native
and JVM links and it travels with its binary (`.prev`, put back, rollback — a record the previous binary
lacked now moves to `.prev` with the binary it describes instead of staying beside the wrong one);
`scripts/bundle/server.sh` writes one for the bundle's own binary (no plugins, origin `bundle`). The
install's `local` mode first asks `hk_build_current` (pure file reads and `shasum`, milliseconds):

1. the bundle's binary is native, linked on THIS machine from THIS kit with exactly the installed plugin
   jars → kept (`ib_result=kept`). That is `just install`: sbt linked it from the same kit minutes ago,
   so a second link would only cost 2 minutes and 12 GB. A CI bundle's record names the CI machine and
   never matches;
2. else the PREVIOUS install's binary (the installers now keep `<dest>.old` until the build step) is
   such a binary → carried over (`ib_result=reused`), the bundle's own binary kept as `.prev`, so
   `plugin rollback` still reaches it. That is a reinstall of the same bundle with the same plugins;
3. else link.

A new kit (any update), a changed, added or removed plugin jar, a JVM-mode binary or another machine
relinks. The toolchain being present is not part of the test: a binary that need not be linked needs no
toolchain.

**What changes for a user.** A first install on a machine now takes about 2 minutes longer and peaks
near 12 GB of memory, plus a one-off GraalVM CE download of ~325 MB (macOS aarch64 tarball,
325 442 578 B; Linux x64 339 260 460 B; checked 2026-10-18) unpacked to ~680 MB under
`<data>/toolchain/`. Later installs of a new bundle relink (~2 minutes, no download); reinstalls of the
same bundle, and `just install`, do not. Machines under 12 GB get the prebuilt binary, as before D5.

**Installer surface.** `--help` prints the header (both installers); `just install` documents the default
and passes `--prebuilt`/`--jvm` through. **Regression:** the installers no longer count the plugin jars
themselves (`ib_build` does, with `hk_plugin_jars`, which is safe on a missing directory); cdd16b48's fix
is kept covered by a test that runs the WHOLE installer of the platform with no `<data>/plugins`.

**Tests.** `kit-scripts-test.sh` (install-build, 25 checks; installer, 3): the mode; the default's steps
with GraalVM present and absent; fallbacks for native-image (exit 3), a refused pre-flight (exit 2),
GraalVM not provisioned, a plugin jar installed (the message), no prebuilt binary (fails); with the REAL
`histmersion-toolchain`: 8 GB of memory (nothing downloaded or linked), a SHA-256 mismatch on a fake
tarball (no link), a good pinned download (provisioned, then linked); the record: an in-place reinstall
kept, the bundle's binary built here kept, built on another machine or without the installed plugin
linked, the previous install's binary carried over with the bundle's as `.prev`, a changed jar or a new
kit linked; `--prebuilt` (nothing run; plugins noted); `--jvm` and its fallback; and the whole installer
(temporary HOME and install, fake `launchctl`/`systemctl`/`curl`, no plugins directory, `pipefail`):
exit 0, the server linked by default, `--help`. `relink-test.sh` (+4): the record's fields after a
relink, current vs stale, rollback moving it with its binary, JVM mode's record.

### 20.2 D4: one plugin per open-access index

**Ids.** `oa-unpaywall`, `oa-core`, `oa-openalex`, `oa-landing` — registering the fetchers `unpaywall`,
`core`, `openalex`, `landing`, whose ids did not change, so `acquire.chain` (default
`archive-org,unpaywall,core,openalex,pmc,arxiv,landing,pmc-text`) orders them exactly as before and the
chain as it runs is the same. Why the `oa-` prefix, when `archive-org`, `arxiv` and `pmc` have none:

- those three are named after the repository that holds the file; these four are *indexes* of where
  copies live (and `landing` is no provider at all, but the step that follows their repository
  locations), so the prefix says what kind of step they are — the precedent is §19's `media-`/`events-`;
- `core` alone reads as Histmersion's own core (`HISTMERSION_PLUGIN_CORE_ENABLED`, a `core` block), and
  bare `landing` says nothing;
- `oa-` is the former id's prefix, so `oa-index`'s successors are found next to it in every sorted list
  (`--self-check`, `/api/about`, the web About window).

| plugin | fetcher | its own settings (defaults resource) | Histmersion settings it still reads |
|---|---|---|---|
| `oa-unpaywall` | `unpaywall` | `contact-email` (`null`; env `HISTMERSION_PLUGIN_OA_UNPAYWALL_CONTACT_EMAIL`) — the e-mail Unpaywall requires, sent only as its `email` parameter | `acquire.unpaywall.url`; `acquire.contact-email` when its own is `null` |
| `oa-core` | `core` | `api-key` (`null`) — a `Secret` REFERENCE (`env:…`, a vault's), resolved by the host before `register`, shown only as the reference | `acquire.core.url`; `acquire.core.api-key` when its own is `null` |
| `oa-openalex` | `openalex` | — | `acquire.openalex.url`, `acquire.contact-email` (the OpenAlex client is shared with discover and followups) |
| `oa-landing` | `landing` | — | `acquire.landing.*` (`acquire.landing.enabled` still switches the step off too), `acquire.zenodo.url`, `acquire.datacite.url`; the Unpaywall record with `acquire.contact-email` |

Each has `enabled` (default `true`, env `HISTMERSION_PLUGIN_<ID>_ENABLED`), its own `Config` type
(`UnpaywallConfig`, `CoreConfig`; the other two none, `BuiltinConfig`) and its defaults resource
`META-INF/histmersion/plugins/<id>.conf`, which is also its key schema: a key of another plugin's fails
that plugin alone, as before. **No e-mail or key is in the repository:** both defaults are `null`, and
the contact stays a per-install setting (the installers pass `HISTMERSION_ACQUIRE_CONTACT_EMAIL` on).
Endpoints stay host settings for the reason §16 gave (moving them would rename every documented key and
env variable for no change in behaviour); a plugin block holds what is the user's own — a contact, a
credential.

**Shared records.** The four plugins' fetchers come from ONE `OaIndex` per generation. The OpenAlex and
DataCite records (`oa:records:<doi>`) and the Unpaywall record (`oa:unpaywall:<email>:<doi>`) are memoised
per fetch, so each upstream is asked at most once per work. The landing step's Unpaywall record is
passed by name, so a disabled landing step asks nothing.

**The landing step reads an index only while that index's plugin is on (follow-up, 2026-10-18).** Until
then it read Unpaywall's and OpenAlex's records whichever of `oa-unpaywall`/`oa-openalex` was switched on,
so switching an index off did not stop requests to it. Decided semantics: the landing step reads an
index's record ON BEHALF OF that index's plugin, so

| `oa-unpaywall` | `oa-openalex` | the landing step reads |
|---|---|---|
| on | on | Unpaywall's and OpenAlex's locations, then the DataCite landing page — the default, unchanged |
| off | on | OpenAlex's locations, then the DataCite page; Unpaywall is never asked |
| on | off | Unpaywall's locations, then the DataCite page; OpenAlex is never asked |
| off | off | the DOI alone: a Zenodo DOI's record (Zenodo's API), else the page DataCite says the DOI resolves to; a DOI DataCite does not know is a miss |

Why: switching a plugin off is the user saying "do not use this service" (a contact they do not want
sent, terms they do not accept, an index that is down); a request made for the landing step is still a
request to that service. With both off the step still has a legitimate lead of its own, because Zenodo
and DataCite are ITS settings (`acquire.zenodo.url`, `acquire.datacite.url`), not an index plugin's. A
publisher link from Crossref metadata was considered and rejected: the landing step never reads a
publisher's page (`acquire.landing.deny-*`, #1580), so it would add requests and no copy; the plain
`doi.org` link is never followed blind for the same reason.

- **Contact:** the landing step now asks Unpaywall with `oa-unpaywall`'s contact (its own
  `contact-email`, else Histmersion's), not always Histmersion's — so with a contact of its own the two
  steps share one memo key and Unpaywall is asked ONCE per work (it was asked twice, once per contact).
  With no contact anywhere Unpaywall is not asked, as before.
- **How the step knows:** `oa-unpaywall` and `oa-openalex` tell their `OaIndex` as they register
  (`unpaywallRegistered(email)`, `openalexRegistered()`, only when their fetcher was added); the landing
  step reads that at fetch time, so the order of the plugins does not matter.
- **Reloads:** because the landing step reads what the other two registered, `oa-unpaywall`,
  `oa-openalex` and `oa-landing` are carried over a reload together or rebuilt together
  (`BuiltinFetchers.LandingGroup` in `Plugins.carried`): switching `oa-unpaywall` off on the fly also
  rebuilds `oa-landing`, which then stops asking Unpaywall. `oa-core` is independent of the three.
- **Default unchanged:** with every plugin on, the requests are byte-identical — the same URLs in the
  same order, one per upstream per work (the test asserting the exact list passes on the code before
  the change too).

**The former `oa-index` (`server/plugins/LegacyPlugins`, pure).** Applied by `Plugins.load` before
`PluginHost.build`, on every load and reload:

- `histmersion.plugins.oa-index.enabled` is copied to each successor whose block does not set `enabled`
  (so `oa-index.enabled = false` switches all four off, and `oa-core.enabled = true` beside it keeps CORE);
- another key of the block goes to the successor whose defaults have it (`api-key` → `oa-core`,
  `contact-email` → `oa-unpaywall`) unless that successor sets it; a key none has is a warning and is
  ignored — `oa-index` itself only ever accepted `enabled`;
- the block is then removed, so no "configures no plugin" warning;
- `HISTMERSION_PLUGIN_OA_INDEX_ENABLED` is copied into the environment the host layers as each
  successor's `HISTMERSION_PLUGIN_<ID>_ENABLED` that is not set — so it keeps the precedence it had (the
  environment over the file, also over a successor's own `enabled` in the file);
- `oa-index` in `histmersion.plugins.order` is replaced in place by the four (those not listed already);
- every one of these adds a deprecation warning to the report: `--self-check` (`- warning: …`, exit code
  unchanged), `/api/about`, `/api/config`, `histmersion about`, the web About window;
- a reload whose changed key is under `histmersion.plugins.oa-index` rebuilds the four (`Plugins.carried`
  expands the id);
- `oa-index` is reserved: `PluginTool.hostIds` names it, so a third-party plugin cannot take it (its
  settings would be read as the successors').

The live config has no `plugins.*` key, so for it nothing is mapped and nothing is warned.

**Native image.** The four defaults resources, `UnpaywallConfig$`/`CoreConfig$`'s derived readers and
`Secret$`'s reader were recorded with the tracing agent during a `--self-check` (pinned GraalVM CE
25.0.1) with a legacy `oa-index` block, `order`, the legacy variable, `oa-core`'s secret and
`oa-unpaywall`'s contact; the stale `oa-index.conf` resource entry was removed by hand.

**Tests.** `BuiltinFetchersSuite` (+14, then +8 for the landing step's switches: all on (the exact
requests), each index off, both off (DataCite; a Zenodo DOI's API), `oa-unpaywall`'s own contact shared,
no contact, the reload group): the seven built-ins and each `oa-*` plugin's one fetcher; each
index switched off by file or env with the chain otherwise unchanged; a setting of another index fails
one plugin; `oa-unpaywall`'s contact (own, env, Histmersion's, none = not asked); `oa-core`'s key (a
secret reaches the `Authorization` header and only its reference the report; Histmersion's key; none =
not asked; an unset `env:` fails it alone); and through `LegacyPlugins`: no legacy config = no warning,
`oa-index.enabled = false`, a successor's own switch, the legacy variable and its precedence, sub-keys
routed and an unknown one warned, `order`, a reload. `LegacyPluginsSuite` (7, pure), `PluginToolSuite`
(the four ids and the reserved former id), `ProviderPluginsSuite` (7 + 8 + 3 built-ins). `smoke.sh` checks
the four registered lines on the native binary, and a `--self-check` with `oa-index.enabled = false`
plus `oa-core { enabled = true, api-key = "env:…" }`: three disabled, CORE registered, the reference shown
and never the key, the deprecation warning.

## 21. As built (kit follow-ups: CI kits, the TCC check, GPG verification at each start; 2026-10-18)

#2028, §18's "not done" list less `-H:LayerCreate`.

### 21.1 CI publishes each platform's relink kit

`.github/workflows/bundle.yml`, after the bundle is packaged, on every platform of the matrix:
`shasum -c` over `dist/histmersion/kit/SHA256SUMS` (only a consistent kit is published), then
`histmersion-<ref>-<platform>-kit.tar.zst` — `kit/` (jars in class-path order, `native-image.args`,
`main-class`, `kit.json`, `SHA256SUMS`, `graalvm-version`, Sloth), `libexec/` (kit-common, the GraalVM
pins, the identity script, the verifying launcher) and `bin/histmersion-{relink,toolchain,signing,server-jvm}`
— with its `.sha256` and the kit's `kit.json` as `…-kit.json`, so its version, commit, GraalVM and SPI can
be read without downloading ~100 MB.

| run | kit uploaded |
|---|---|
| a `v*` tag | attached to the GitHub release, next to the bundle archive |
| `workflow_dispatch` | a workflow artifact `relink-kit-<platform>`, kept 7 days (`compression-level: 0`: zstd already) |
| push to master, pull request | packaged and checked, not uploaded — three ~100 MB kits per run would exhaust a private repository's artifact storage, the reason the bundle itself is not uploaded either |

A bundle already carries its kit, so a relink never needs the source; the separate kit repairs an install
whose kit fails its `SHA256SUMS` (the relink refuses it) without the ~1 GB bundle: extract it over the
install of the **same** version (README, "Relink kits from CI"). Nothing checks that the kit's version is
the installed bundle's — `kit.json` names it, and a mismatched kit would link a server of another version
than the bundle's PostgreSQL and converter. `actionlint` passes (its one warning, SC2155 in the Linux
install step, predated this and was fixed). The packaging step was run locally against a fake
`dist/` (names, contents, `.sha256`); the workflow itself has not run yet.

### 21.2 TCC persistence with an untrusted identity

What a TCC grant stores for a non-bundled binary is its path and its code requirement (`csreq`), and on
a later access the binary at that path must satisfy that requirement. So the question splits:

- **Automated (the proxy).** `kit-scripts-test.sh` with `HISTMERSION_TEST_KEYCHAIN=1` (macOS, opt-in;
  a temporary keychain appended to the search list for the signing, the list saved, restored and
  compared, the keychain deleted; the login keychain never read or changed): an identity made with
  `--no-trust` signs copies of `/usr/bin/true` and `/usr/bin/false`; their cdhashes differ, their
  designated requirements are equal (`identifier "com.histmersion.server" and certificate leaf = H"…"`, no
  cdhash), and **each satisfies the other's** (`codesign --verify -R "=<requirement>"`) — what TCC re-checks
  after a relink. The control: the same two signed ad hoc have different `cdhash` requirements and
  satisfy neither. `codesign --verify` passes on the untrusted signature. 10 checks, run 2026-10-18: all
  pass.
- **Manual (the user's, once).** Whether TCC honours a requirement anchored on an UNTRUSTED certificate
  is decided inside `tccd`, and only a real prompt shows it. `scripts/bundle/tcc-check.sh` does
  everything but the click: `start` compiles a 20-line probe (`opendir`/`readdir` of `~/Documents`, a
  variant string compiled in so every build has a new cdhash), signs it with a new untrusted identity in
  a temporary keychain (or `--identity NAME`), and runs it as the throw-away LaunchAgent
  `com.histmersion.tcc-check` (launchd makes it its own responsible process, as the server is; run from
  Terminal it would be covered by Terminal's grant) → macOS asks for Documents access: **Allow**.
  `relink` swaps a new variant in at the same path, signed by the same identity, and runs it again:
  `ALLOWED` with no prompt = **the grant persisted**. `control` does the same ad hoc (expected: a new
  prompt or `DENIED`), showing the check tells the two apart. `result` prints every answer and the
  current requirement; `cleanup` unloads and deletes the agent, the keychain and the work directory
  (`~/Library/Caches/com.histmersion.tcc-check`). The grant itself stays in System Settings › Privacy &
  Security › Files and Folders as `histmersion-tcc-probe` — `tccutil reset` takes only a bundle id, and
  without one it would reset every app's Documents grant. Tested against a launchd stand-in (always) and
  with a real temporary keychain (opt-in). **Run with a real prompt on 2026-10-23: the grant persisted**
  — after `relink` the new binary (another cdhash, the same untrusted identity) read `~/Documents` with
  no prompt, and the ad-hoc `control` was asked again. So `setup-macos` leaves the identity untrusted by
  default (#3647); `--trust` keeps the old behaviour for whoever wants it.

### 21.3 GPG verification of the server binary at each start

§18 checked the native binary only at relink time; at a start the service ran whatever was in
`bin/histmersion-server`. Now, with GPG provenance on:

- **`libexec/histmersion-server-verified`** (`scripts/bundle/server-verified.sh`, bash 3.2) is what the
  LaunchAgent / systemd unit starts. It requires `bin/histmersion-server.sha256` (`<sha256>
  histmersion-server`, the record relink.sh already wrote) to carry a good signature
  (`bin/histmersion-server.sha256.asc`) by the configured key, and the binary's SHA-256 to be the one in
  it; then it `exec`s the binary — same pid, `argv[0]` the binary, so `pgrep -f <install>/bin/histmersion-server`
  and the installers' wait still work. Otherwise one line to `server.log` (why, and the two ways out:
  `sign-binary`, `off-gpg`) and exit 78 (EX_CONFIG); launchd retries about every 10 s and systemd every
  5 s, so an imported key or a re-signed binary is picked up without a reinstall, and a refused binary is
  never run. Provenance switched off under it: it starts the binary unverified and says so.
- **Not changed without GPG.** The service definition points at the launcher ONLY while provenance is on;
  otherwise it starts `bin/histmersion-server` directly, as before — so the default install's TCC identity,
  process tree and start-up are untouched. That is why the choice is the definition's, not the launcher's.
- **The LaunchAgent environment.** Its PATH is `/usr/bin:/bin:/usr/sbin:/sbin` and it has no
  `GNUPGHOME`; neither Homebrew's nor MacGPG2's gpg is on it. `use-gpg` therefore records the gpg program
  and `GNUPGHOME` it ran with (`<data>/signing/gpg-program`, `gnupg-home`; nothing secret), and the
  launcher uses them (`HISTMERSION_GPG` and `GNUPGHOME` still win).
- **`histmersion-signing`.** `use-gpg <key> [--no-restart]` also signs the installed binary and `.prev`
  (`sign-binary`) and points the service at the launcher; `off-gpg [--no-restart]` points it back. Pointing
  rewrites `ProgramArguments.0` (`plutil -replace`) or `ExecStart=` and reloads: `launchctl bootout` +
  `bootstrap` when loaded (a plist change needs that; `kickstart` reuses the loaded definition), or
  `daemon-reload` + `restart` when active. A definition that starts another install's server is left
  alone. `sign-binary` signs only a binary whose record does not verify — it is how the user vouches for
  a binary; `verify-binary` runs the launcher's check (exit 2 when the installed binary fails); `status`
  says how the service starts.
- **Relink, JVM mode, rollback.** Unchanged for native relinks (the checksum was already signed after
  codesign). `--jvm` now signs the launcher it puts in `bin/` too — before, a JVM-mode `bin/histmersion-server`
  had no signed record. `--rollback` with provenance on refuses (exit 2, before anything moves) a previous
  binary whose record does not verify, naming `sign-binary`; the records already travel with their binary
  (`.sha256.prev`, `.sha256.asc.prev`). A relink whose signing fails under `--unsigned` swaps in a binary the
  service will refuse — the restart then fails and the relink rolls back (exit 4), which is the point.
- **Installers.** `ib_service_program` (install-build.sh) runs after any codesign (which changes the
  SHA-256): provenance off → `bin/histmersion-server`; on → `sign-binary` (installing a bundle is the
  moment you vouch for it; a relinked binary is already signed) and the launcher. When signing fails (no
  secret key, pinentry cancelled), the install points the service at the binary with a WARNING and the fix
  (`histmersion-signing use-gpg <key>`): an install never leaves a service that refuses to start.
  `install-macos.sh` writes the program into `ProgramArguments`, `install-linux.sh` into `ExecStart`; the
  LaunchAgent environment merge (`la_env_*`) is unchanged. A bundle without the launcher (older) keeps the
  direct start.
- **A key that signs with a subkey.** `use-gpg` stores the PRIMARY key's fingerprint; GnuPG's `VALIDSIG`
  names the signing subkey first and the primary last. The binary check and `verify-plugins` now accept
  either (before, a subkey-signing key failed `verify-plugins`); `verify-file` still prints the signing
  key's, which a plugin's pinned signer (`index.json`) holds.
- **Not covered.** The check and the `exec` are two steps: a binary swapped between them runs unverified
  (a relink swaps only while it holds its lock and then restarts the service, so this needs a local
  attacker who can already write `bin/`). The launcher script itself, `kit-common.sh` and the plist are
  not verified — something that can rewrite them can also rewrite the binary's start; the check guards the
  binary against being replaced, not the install directory against its owner.

**Tests.** `provenance-test.sh` (36; real gpg in temporary GNUPGHOMEs, keys without a passphrase): provenance
off — the direct start and the launcher's pass-through; `use-gpg`'s records; a LaunchAgent-like start
(`env -i`, PATH without gpg) verified and `exec`'d (its parent is the caller); refused (78, not run): a
changed binary, no signature, another key's good signature, a validly signed checksum of another binary,
a changed record, the signer missing from the keyring, no gpg; `sign-binary` (and `.prev` under its rotated
names), `verify-binary`; a subkey-signing key; the service definition pointed and back (systemd unit and
— macOS — plist, with fake `launchctl`/`systemctl`; reloads, `--no-restart`, another install's left
alone); the whole installer (`--prebuilt`, temporary HOME, fake service, `codesign` and health) off, on,
and on-but-unsignable. `relink-test.sh` (+6, fake gpg): the launcher accepts a relinked binary; rollback
with provenance keeps it verifiable; a rollback to the never-signed stock binary refused, then allowed after
`sign-binary`; `--jvm` with and without provenance. `smoke.sh` runs the REAL native binary's `--self-check`
through the launcher (a copy of `bin/`, a temporary GNUPGHOME) and sees a changed binary refused with 78.

## 22. AI providers as plugins (design, 2026-10-19)

The user asked to "consider making AI provider a plugin as well." This section inventories what the
providers are today, where provider-specific code sits, proposes an `aiProviders` registry, and says
which parts are worth building. §23 is what was built from it.

### 22.1 Inventory: the six providers today

A `provider:model` reference (`core/Agents.ModelRef`) names one of six providers, a CLOSED enum
(`Agents.ProviderKind`). The engine sees only `agents/LlmClient` (`run(LlmCall[Out]): AgentResult`); the
factory `LlmClient.factory(rt)` matches on the enum.

| provider | how a step runs | tool use | typed answer | tokens | plan / quota | login check | definitions |
|---|---|---|---|---|---|---|---|
| `anthropic` | sttp-ai `ClaudeAgent`, the HOST's tool loop (one HTTP call per turn) | the host's `Toolbox` tools in process | sttp-ai's response schema; host validators | each response's usage (interceptors per call) | metered: `agents.prices`, allowances | `ANTHROPIC_API_KEY` present (doctor: `GET /v1/models`) | — |
| `openai` | sttp-ai `OpenAIAgent`, host loop | as above (`strict-tools`) | as above | as above | metered | `OPENAI_API_KEY` | — |
| `local` | sttp-ai `OpenAIAgent` on any OpenAI-compatible URL, host loop | as above | as above | as above | metered (price `0/0`) | none needed | — |
| `claude-code` | ONE `claude -p` process per step attempt (`ClaudeCodeClient`, `CliProcess`): the CLI runs the loop | the agent MCP endpoint `/mcp/agent`, reached with a per-step RUN TOKEN bound to the role's tools (`RunTokens`) | `--json-schema`; host validators | `stream-json` `modelUsage`; first-call probe from the stream | Claude plan pool (`PlanPools`): `rate_limit_event` readings, floors, the quota-guard mod, the one-turn probe refresh, `ModelLimitReached` cool-down | `claude --version`, `claude auth status` (method and plan only); loopback rule | the generated headless plugin + role settings (`AgentGenerator`/`AgentTrees`) |
| `codex` | `codex exec --json -p <role>` per attempt (`CodexExecClient`) | the agent MCP endpoint via the generated profile | `--output-schema` (OpenAI strict form, `StrictJsonSchema`) | `turn.completed.usage` | ChatGPT plan pool: `codex app-server` → `account/rateLimits/read` (throttled) | `CODEX_HOME/auth.json` EXISTS (never read); loopback rule | the generated `CODEX_HOME` profiles |
| `opencode` | `opencode run --agent <role> --format json` (`OpencodeRunClient`) | generated agent, MCP header with the run token | schema in the message, text parsed | `step_finish.tokens` | ChatGPT plan when `chatgpt-login` and `openai/…`, else metered | none | generated `XDG_CONFIG_HOME`/`OPENCODE_CONFIG_DIR` |

There is no sttp-ai-style catalogue: model ids are free text in `agents.roles.*`; the doctor compares
them with the provider's free `…/models` listing.

### 22.2 Where provider-specific code leaks out of `LlmClient`

| place | what it knows |
|---|---|
| `core/Agents.ProviderKind` | the six names, `isCli`; `ModelRef.parse` and `ModelChains.Target.parse` refuse any other name |
| `core/Agents.Prices.metered` | a sentence per CLI provider explaining why it is metered |
| `core/PlanQuota.poolOf/paid/Logins` | which provider draws on which plan (`claude-code`, `codex`, `opencode:openai/…`); `PoolId` is a closed enum |
| `core/AgentsConfig.providers` | one typed block per provider (`agents.providers.<name>.*`), `provider(kind)` |
| `agents/LlmClient.factory/fromConfig` | the match, the key messages |
| `agents/AgentDoctor` | a check list per provider (HTTP listing; claude/codex/opencode executables, login, loopback) |
| `agents/PlanPools` | `loginGates`, the Codex app-server read, the Claude probe, `claudeEnv` |
| `agents/ModelPicker` | the cool-down length from `claude-code.limit-cooldown-minutes`; the "counts what claude -p reported" note |
| `agents/AgentsHandlers.covers` | which provider targets a plan pool covers |
| `agents/AgentGenerator`, `AgentTrees`, `CliRuntime` | the three runtimes' file formats and homes |

The engine (`Engine`), the pipelines, the managers, approvals, budgets, allowances, the review panel and
the toolbox do NOT branch on the provider: they see `LlmClient`, `Billing` and `PoolId`. That is the
seam a registry can take over without touching them.

### 22.3 The proposal: an `aiProviders` registry

One more registry on `PluginContext` (compatible: a default that refuses, `Spi.Version` stays 1), keyed
by the PROVIDER NAME — the part before the colon of `provider:model`:

```scala
trait AiProvider:
  def title: String
  def description: String = ""
  def capabilities: AiCapabilities = AiCapabilities()        // tools, structured output (flags)
  def spendsPersonalPlan: Boolean = false                    // refused beyond loopback, like claude-code
  def check(model: String): Either[String, Unit] = Right(()) // can a client for `model` be built now (a key, a URL)
  def models(http: AiHttp): Either[String, List[String]] = Right(Nil) // a FREE listing, for the doctor
  def doctor(online: Boolean, http: AiHttp): List[AiCheck] = Nil      // spends nothing
  def turn(request: AiTurnRequest, http: AiHttp): Either[AiFailure, AiTurn] // ONE model call
```

- **A turn, not a step.** The provider answers ONE model call: the system prompt, the conversation so
  far (user, assistant text and tool calls, tool results), the tools offered (name, description, JSON
  Schema), the answer's JSON Schema, `max-tokens`, and the iteration. It returns text, tool calls, a
  stop reason, usage and the model that served. The HOST runs the loop — sttp-ai's, through a custom
  `AgentBackend` — so the tools stay the host's in-process `Toolbox` (allowlist per step, result caps,
  the read-only `query` role), every interceptor (trace, run budget, `max-llm-calls-in-flight`, the
  first-call probe, the allowance guard) sees each call, and the typed answer is decoded and validated
  by the host as for `anthropic`/`openai`. A plugin cannot loosen a budget, a tool allowlist or a
  validator, because it never holds them.
- **Failures are values.** `AiFailure.Error(message)` is a provider error (the run pauses with
  `provider`, as an HTTP failure does today); `AiFailure.LimitReached(message, until)` is the plan's own
  limit — the host raises `ModelLimitReached`, so the chain's next model takes over and the cool-down
  applies. An exception is an error.
- **Credentials are `Secret`s** in the plugin's own block (`api-key = "env:GEMINI_API_KEY"`, a vault
  reference), resolved by the host before `register` (§3.7). The provider never reads a credential file
  or another tool's login; the host never reads one either (the rule of the CLI providers holds).
- **HTTP.** `AiHttp` is a host client for model calls: `post`/`get` with a per-call timeout, HTTP/1.1,
  the host's User-Agent, never logging a header or a body (a key travels in a header). The acquisition
  client (`PluginHttp`) is the wrong one: its retry and back-off policy is for polite scraping, and a model
  call may run minutes.
- **Billing.** A plugin provider's models are METERED: priced in `agents.prices` (`0/0` for a free local
  server) and governed by allowances (`<provider>:*` or one model) and the USD budgets, which already
  work for any provider target. Plan pools stay the host's two (below).
- **What stays host-side:** prompt construction (the definitions, includes, the writing guide), budgets,
  allowances, plan pools and floors, approvals, schema validation and retries, the tool loop and tool
  sandboxing, the trace, the pick before every call, dry runs, the loopback rule.

**Built-ins.** Today's six become built-in plugins, `ai-anthropic`, `ai-openai`, `ai-local`,
`ai-claude-code`, `ai-codex`, `ai-opencode`, each enabled by default and switched off with
`histmersion.plugins.ai-<name>.enabled = false` (env `HISTMERSION_PLUGIN_AI_<NAME>_ENABLED`). Their
registry entries are `HostAiProvider`s, recognised by the host and run with today's code — exactly as the
media collections and event catalogues are (§19): **config keys are layered, not mapped**, so every
`agents.providers.*` key and env variable keeps its meaning and stays hot (read at each call).

**Resolution.** The server's factory looks the provider name up in `deps.plugins.registries.aiProviders`
— the generation the run is pinned to. A `HostAiProvider` → today's client; a plugin's → the adapter over
`turn`; a built-in that is switched off → "the codex provider is switched off
(histmersion.plugins.ai-codex.enabled = false)"; anything else → unknown provider, as today.
`ModelRef.parse` and `Target.parse` take the registered plugin providers' names as an extra argument (a
pure function still; the default is none), so `agent models set extractor gemini:…` and an allowance on
`gemini:*` are accepted only while such a provider is registered, and every message for a typo stays
as it is.

### 22.4 Hot reload

Adding, switching off or reconfiguring a provider is a change of a `histmersion.plugins.<id>` block:
`config reload` (or the watcher) rebuilds that plugin alone into a new generation (§17). A run carries
its pin for its whole life, so a run that started on a provider keeps that generation's provider (and
its decoded config and resolved secrets) to its last step; the next run sees the new one. The built-in
entries read nothing at registration, so like the media/events built-ins they are carried across a change
of a host key. `agents.providers.*` changes need no plugin rebuild: they are read at each call, as today.

### 22.5 Native image

Nothing new: a third-party provider is a plugin jar, linked by a relink (§18), and carries its own
reachability metadata; the six built-ins are in every image. The adapter uses sttp-ai's public
`AgentBuilder.apply(config => AgentBackend)` and no reflection. The six defaults resources are recorded by
the tracing agent during a `--self-check`.

### 22.6 Migration: zero behaviour change

- No config key is renamed; no env variable changes. The six plugin blocks are new and default to
  enabled. Nothing reads `LegacyPlugins` because nothing was split.
- A deployment that never writes `histmersion.plugins.ai-*` resolves every `provider:model` exactly as
  before (same clients, same messages, same billing). The only visible change is six more rows in
  `--self-check`, `/api/about`, `histmersion about` and the web About window, each "registered
  (ai-provider:<name>)".
- No migration (V097 is not needed: nothing stored changes; model wires are text).

### 22.7 Recommendation

**Worth it, in part.**

1. **Build: the registry, the turn-level SPI and the adapter.** Small (one file in plugin-api, one adapter),
   and it opens the one gap a user can feel: a provider sttp-ai does not ship (Gemini, Mistral, Bedrock,
   a corporate gateway with its own auth, a local server with a non-OpenAI API) needs only a `turn`, and
   it inherits every guard the host already has because the host keeps the loop.
2. **Build: the six as built-in, switchable plugins** with `HostAiProvider` entries. Cheap, and it gives
   the About/self-check listing, one switch per provider, and resolution through the pinned generation —
   the same shape §19 gave media and events.
3. **Do not build: moving the built-ins' code into plugin-api-only modules.** The HTTP ones are thin
   wrappers over sttp-ai (which plugin-api must not depend on: it is sconfig + kindlings only, D1); the
   CLI ones are mostly HOST machinery — run tokens bound to an agent definition, the agent MCP endpoint,
   the generated trees, the plan pools and their readers, the quota-guard mod. Moving them would mean
   lending all of that through the SPI and renaming every `agents.providers.*` key and env variable for
   no behaviour gain (the reasoning of §16 for the fetchers).
4. **Do not build now: a provider-run-loop ("runtime") SPI for third-party CLIs.** A runtime that runs
   its own loop needs, from the host, a run token, the agent MCP URL, and — to be useful — generated
   agent definitions in its own file format, a reading of its plan, and a legal review of its terms (the
   `claude -p` analysis of docs/research-model-providers.md was needed for one vendor). The shape is known
   (`AiRuntime.generate(definitions): Map[path, text]` + `step(request, McpAccess(url, token))`), but it
   should wait for a concrete runtime to ask for it; the host-loop SPI covers HTTP APIs, which is where
   the new providers are.
5. **Do not build now: plugin plan pools.** `PoolId` is closed, its floors are `agents.quota.<pool>` keys,
   its readers are per vendor and the UI draws each pool. Opening it is a config, schema (`plan_quota_*`
   rows keyed by pool) and UI change whose only user would be a subscription runtime — item 4. A plugin
   provider is metered, and allowances (tokens/USD per rolling window on `<provider>:*`) already cap it.

## 23. As built (AI providers, 2026-10-19)

§22.7's items 1 and 2: the `aiProviders` registry with a turn-level SPI, the adapter that runs a plugin's
provider in the host's own loop, and today's six providers as built-in, switchable plugins. Items 3–5
(the built-ins' code moved into plugin-api-only modules, a runtime SPI, plugin plan pools) are not built,
for §22.7's reasons. No migration; no config key or env variable renamed.

**The SPI (plugin-api, still `Spi.Version` 1)** — `AiProvider.scala`:

| type | shape |
|---|---|
| `AiProvider` | `title`, `description`, `capabilities` (`tools` true, `structuredOutput` false), `spendsPersonalPlan` (false), `check(model)`, `models(http)` (`Right(Nil)`), `doctor(online, http)` (`Nil`), `turn(request, http)` |
| `AiTurnRequest` | `model`, `system`, `messages`, `tools` (offered on THIS call), `answerSchema` (JSON Schema text), `maxTokens`, `iteration`, `maxIterations` |
| `AiMessage` | `User(text)` · `Assistant(text, toolCalls)` · `ToolResult(callId, tool, output)` |
| `AiTool` / `AiToolCall` | `name, description, inputSchema` (JSON Schema text) / `id, tool, input` (JSON text) |
| `AiTurn` | `text`, `toolCalls`, `stop` (`EndTurn · ToolUse · MaxTokens · Other`), `usage: Option[AiUsage]` (`input` counts cached tokens too), `servedBy` |
| `AiFailure` | `Error(message)` · `LimitReached(message, untilEpochMs)` |
| `AiHttp` | `post(url, headers, body, timeoutSeconds)`, `get(url, headers, timeoutSeconds)` → `AiHttpResponse(status, body, headers)`; `AiHttp.Offline` |

`PluginContext.aiProviders: Registry[AiProvider]`, refusing by default (a context or plugin written for
the earlier registries compiles unchanged; tested).

**Model references (core).** `ProviderKind` gained `Plugin(name)`; its `values` are still the six host
providers (an explicit list: an enum with a parameterised case has none of its own). `ProviderKind.parse`,
`ModelRef.parse`, `ModelChains.parseChain/parseModels/parseAllowances/parseAllowance` and `Target.parse`
take `known: Set[String] = Set.empty` — the names plugin providers are registered under. A host name is
always the host's (a plugin cannot shadow `local`), an unknown one gives today's message ("unknown provider
'x' in 'x:y' (one of: …)", the plugins' names appended when there are any). The server passes
`BuiltinAi.known(deps.plugins.registries.aiProviders)` — the pinned generation's — wherever it parses: the
picker (chains, saved chains, `--model`, allowances, cool-downs), `agent models set`, allowances, `--model-for`,
`agent compare`'s billing and the managers' model overrides. A plugin provider's model is metered
(`PlanQuota.poolOf` is `None`, `paid` is true): priced in `agents.prices`, capped by allowances.

**The built-ins (`server/agents/AiPlugins.scala`).** `BuiltinAi.plugins`: `ai-anthropic`, `ai-openai`,
`ai-local`, `ai-claude-code`, `ai-codex`, `ai-opencode` (the same `Builtin` wrapper as the fetchers, media
and events), each registering a `HostAiProvider(plugin, kind)` under the provider's name; defaults
resources `META-INF/histmersion/plugins/ai-<name>.conf` (`enabled = true`, env
`HISTMERSION_PLUGIN_AI_<NAME>_ENABLED`). They are in `Plugins.providers`, so `Plugins.offline` (a test's
`Deps`) has them, and in `Plugins.carried`'s independent set (a host-key change never rebuilds them). A
plugin may not register a host provider's name, even with the built-in switched off (`BuiltinAi.reserved`).

**Resolution.** `LlmClient.factory(rt, providers, http)` — the server's — first does
`BuiltinAi.lookup(ref.provider, providers())`; `AgentsHandlers.serverWiring(deps)` passes
`() => deps.plugins.registries.aiProviders`, read at each pick (a run is pinned to its generation, so its
provider stays the one it started with across a reload). A `HostAiProvider` → `LlmClient.host(rt)`, which
is the former factory, unchanged; a plugin's → `PluginAiClient.client`; none → "the codex provider is
switched off (histmersion.plugins.ai-codex.enabled = false)" or "no AI provider 'x' is registered". The
picker shows that reason for the model as not ready, exactly as a missing key is shown today. A test's
own factory (`AgentsHandlers(deps, h, factory)`, the scripted clients) is untouched.

**The adapter (`server/agents/PluginAiClient.scala`).** `AgentBuilder[Identity, PluginModel.type](conf =>
Turns(…))` — sttp-ai's public builder over a custom `AgentBackend` — configured by the same
`LlmClient.configure` the API providers use, so the loop, the step's tools (the allowlist: a tool the step
does not offer answers "not found"; tested with a `query` call), the interceptors (each turn is one call:
the trace, the run budget, the LLM slot, the first-call probe, the allowance guard), the response decoding
and `max-tokens` are sttp-ai's and the host's. `includeTools = false` on the last iteration reaches the
provider as an empty tool list. A `LimitReached` is `ModelLimitReached` (the cool-down and the chain's
next model, as for `claude -p`); an `Error`, an exception or a `null` is an `IOException` (a provider
error: the run pauses with `provider`). `client` refuses a `spendsPersonalPlan` provider beyond loopback
and passes the provider's own `check`. `HostAiHttp`: java.net.http, HTTP/1.1, 30 s connect, a per-call
timeout, `HttpPolicy.userAgent("Histmersion/<v> (agents)")` replacing any User-Agent the plugin sets, and
errors that carry the exception's message only (never a header); its `HttpClient` is built on the first
call, so a server that never calls a plugin provider starts no selector thread.

**Doctor.** `AgentDoctor` takes the registry (`registry`, `aiHttp`). A host provider whose plugin is off
gets a first check `plugin` — fail when a role uses it, else warn — naming the switch; otherwise its
section is as before. Each plugin provider gets a section: `plugin` (its title), its own `doctor` checks
(a level that is no `ok`/`warn`/`fail` reads as warn), its free listing online (`models`; the chained
models present upstream, as for the HTTP providers), and every chained model priced and buildable. Nothing
the plugin throws ends the report.

**Surfaces.** No new endpoint, tool or command: `--self-check`, `/api/about`, `histmersion about` and the web
About window list the six (`ai-<name> 1.0.0 — registered (ai-provider:<name>)`) from the plugin report;
`agent models` and `agent doctor` show plugin providers; `agent models set` accepts their models.

**The test provider (`modules/plugin-fixtures/hello-ai`).** `hello-ai` 0.1.0, compiled against plugin-api
alone, in a jar of its own (never on the server's class path; `plugin-fixtures.txt` names it for the
suites). Its answers are scripted in its block (`replies = [{ when, text, tool, input }]`: the first whose
`when` occurs in the step's answer schema; a `tool` is called once first when offered); the model `limited`
reports its plan's limit; `broken` fails its `check`; an optional `token` secret shows credentials arrive
as references. It opens no socket.

**Tests.** `AiProviderSpiSuite` (plugin-api: defaults, a turn, the offline client, a pre-AI context refusing
the registry, SPI 1); `AgentsSuite`/`ModelChainsSuite` (+4, core: a plugin name parses only when known, a
host name stays the host's, the message lists the plugin names, a plugin model is metered; chains,
allowances and targets with `known`); `AiProvidersSuite` (server, 12, no DB: the six registered and
switched off by file and by env, a plugin's provider beside them, host names refused also with the built-in
off, the reload carry rule, the factory — every host provider resolves exactly as `LlmClient.host`, a
switched-off one names its switch, a plugin's check and the loopback rule, an unregistered one — and the
host's loop over a plugin's turns: the host's tool run, the answer decoded, usage summed over turns, what
the provider was told, a disallowed tool not run, no tools on the last iteration, an interceptor per turn,
the four failure kinds); `PluginAiRunSuite` (DB: the jar discovered and registered with its secret
resolved, the report holding the reference only; `enrich-source` end to end on `hello-ai:echo` through the
server's own factory — classify with a `facets` call in the host, findings, notes, the source written, the
served model recorded; `hello-ai:limited > hello-ai:echo` moving on and cooling `limited`; `agent models`,
the doctor section with the token never shown, `models set` accepted, then refused — and the saved chain no
longer readable — once the plugin is off); `HotReloadSuite` (+1: a reload switching `ai-local` off rebuilds
that plugin alone and `local:*` is out of the chain at once; switched on again, back);
`ProviderPluginsSuite` counts the six. `smoke.sh` checks the six registered in the native binary's
`--self-check` and `ai-codex` reported disabled when switched off.

**Native image.** The six defaults resources were recorded by the tracing agent (pinned 25.0.1-graalce, a
`--self-check` with `ai-opencode` off, temporary config and data directory); the same run added config-reader
lazy-val fields (`AgentAutopilotConfig$`, `AutoApprovalsConfig$`, and later-numbered fields of others) that
the previous recording had missed. A plugin provider's path (the adapter, `HostAiHttp`) is reflection-free
Scala over classes the image already holds; it runs natively only in a relinked binary, as any plugin's code.

**What changes on deploy.** Nothing a user has to do. Six more plugins in every report; `agent doctor`
unchanged unless a provider is switched off. Registration order in reports is by plugin id, as for the
other built-ins (`ai-…` sort first).

**Not done.** A provider-run loop for third-party CLIs, plugin plan pools and moving the built-ins out of
the server (§22.7); a run that is mid-step during a reload is pinned by §17's mechanism (HotGraphSuite
covers the pin; no agents-on test server exists, §17 "Not done"), not by a test of its own.

## 24. As built (plugin dependencies without shading, 2026-10-24)

Shading made every plugin author repeat the same work (an assembly build, relocations), could not be done
with scala-cli at all, and shipped a second copy of any library the server also has under another
package. The plugin's libraries are now ordinary Maven dependencies, resolved at install time together
with what the server ships.

**What a plugin compiles against.** The bundle carries a Maven repository, `maven/` (`server/pluginRepo`,
copied by `scripts/bundle/server.sh`; `histmersion plugin repo [--url]`), with two artifacts at the build's
version: `com.kubuszok::histmersion-plugin-api` (jar, sources, POM: sconfig and kindlings' sconfig
derivation) and `com.histmersion::histmersion-plugin-platform`, a POM-only artifact whose dependencies are the
libraries the server provides to plugins, at the versions the server's runtime class path resolves
(`Runtime / update`, not `V.*`: an eviction can raise one). Third-party jars still come from Maven
Central; only Histmersion's own artifacts live in the local repository. A release attaches the repository
as `histmersion-maven-<tag>.zip`. The template (`plugin new`) uses `//> using repository`, both artifacts
as `compileOnly.dep` (and `test.dep`, since the tests run the plugin), and `publish.*` directives; the
loop is `scala-cli --power publish . --publish-repository ./repo` → `plugin add <org>::<id>:<v> --repo
./repo`. scala-cli resolves a POM-only dependency's dependencies transitively and leaves compile-only
ones out of the published POM (checked with scala-cli 1.18). `--api-jar` became `--repo <dir|url>`.

**The platform is a contract.** `modules/plugin-api/plugin-platform.txt` lists the libraries and
versions (Scala library, sconfig, kindlings sconfig/circe/tapir-schema derivation, circe core/parser,
cats, sttp client 4, tapir core/circe, ox, iron). The build takes the list from that file and the
versions from the server's resolution; `PluginPlatformSuite` fails when they differ or a library is no
longer shipped, so a dependency upgrade that changes what plugins compile against is acknowledged by
editing the file (and named in the release notes).

**Resolution (`modules/plugin-resolver`).** coursier's Java API (`io.get-coursier:interface`, one shaded
jar, its slf4j-api left to the kit's), run on the JVM from `kit/tools/resolver/` beside `kit/lib`. It is
not a dependency of the server: the jar carries legacy native-image configuration of its own, which the
server's image must not read. The kit records `kit/modules` (every module of the server's runtime
resolution, `group:artifact:version`) and `kit/excludes` (the build-wide exclusions). For a set of plugin
jars, each with its POM (beside the jar, `x-1.0.pom`, or the single `META-INF/maven/**/pom.xml` inside it;
none = no dependencies):

1. the plugins are laid out as a scratch Maven repository at the coordinates their POMs state, so coursier
   reads their POMs (parents, properties, dependency management) like any other;
2. each plugin is resolved **alone**; a module the server ships at an OLDER version than the plugin's
   resolution chose is a conflict, refused with the library and both versions (an older or equal version
   is no conflict: resolved together, the server's would be chosen, as Maven chooses the newest);
3. all plugins are resolved **together with the server's modules excluded** — the server's copy of each,
   and everything it depends on, is on the class path already. Forcing the shipped versions instead was
   tried first: it made coursier fetch the POM of every shipped module the plugin touched, network for
   nothing;
4. what remains (not a plugin, not shipped, with a jar) is written to `<data>/plugins/lib/` as
   `<group>-<file>` (two groups publish `core_3-1.0.2.jar`), with `classpath` (the order), `inputs`
   (`<sha256>  <file>` of each plugin jar and POM) and `repositories` (the ones named, merged with the ones
   kept from earlier runs). Exit codes 0 / 1 usage / 2 refused / 3 failed.

**Where it runs.** `histmersion-relink --resolve [--lib] [--repo]… [--check] [<jar>…]` (kit-common
`hk_resolve`, with the install's `maven/` first). `plugin add` fetches the POM with the jar (Maven: required
and checksum-checked; a path or URL: the `.pom` beside it when there is one), resolves the staged plugin
into a staging `lib/`, describes it with that `lib/` on its class loader (PluginTool `describe --lib`),
shows the dependency lines, and after installing (the POM beside the jar) resolves the whole directory into
`plugins/lib`; `plugin rm` resolves again. The relink's pre-flight resolves first, then `check --lib`
(duplicate classes across kit, dependencies and plugins), `plugin-set --lib`, the JVM self-check with
`lib/` on the class path, Sloth over the dependency jars, and native-image with them after the plugin
jars. `histmersion-server-jvm` resolves again only when `lib/inputs` differs from the directory. The
build record's plugin fingerprint covers the POMs; the signed plugins manifest covers the POMs and
`lib/`'s jars (both unchanged for a directory with neither).

**A missing class.** A plugin class that needs a class no jar has threw `NoClassDefFoundError`, a
`LinkageError` that `NonFatal` does not catch: discovery, config decoding and `register` let it escape.
They catch it now, and the plugin fails alone with "class X is missing: the plugin uses a library that is
neither part of Histmersion nor installed with it — install the plugin with `histmersion plugin add`
from a jar published with its POM" (`PluginHost.failure`; PluginTool's describe uses it too).

**Tests.** `pluginResolver` suites resolve offline from a fixture repository (`pluginHelloDeps/fixturesRepo`:
`hello-deps`, whose POM lists `hello-lib` and plugin-api as `provided`); PluginToolSuite describes
`hello-deps` with and without its `lib/`; PluginHostSuite fails a plugin on a `NoClassDefFoundError`;
PluginCmdSuite covers `add` with a POM, the refusal, `--repo` and `rm`; relink-test and kit-scripts-test
cover `--resolve`, the pre-flight, Sloth and native-image arguments and the JVM launcher's re-resolution;
`smoke.sh` relinks a real bundle with `hello` and `hello-deps`, installed by coordinates from the fixture
repository.

**Not done.** The plugin-set digest (and so `plugin status`) still covers the plugin jars only, not their
POMs or `lib/`: a changed POM beside an unchanged jar shows no REBUILD NEEDED (the build record does
notice it). A plugin's dependencies are not isolated from each other's versions beyond coursier's
newest-wins choice across plugins.

## 25. As built (the plugin testkit, 2026-10-24)

A plugin's own tests run it the way the server does, without the server, the database or Postgres.

**Modules.** The host side of the SPI that needs neither moved out of `server` into
`modules/plugin-host` (`com.histmersion.pluginhost`, depends on core and plugin-api): `PluginHost`
(discovery, config layering and type checks, secrets, staged registration; the built-in providers'
reserved names in `ReservedNames`, which `ReservedNamesSuite` holds equal to the server's registries;
host-owned contributions marked `HostOwned`), the acquisition client `PolicyHttp` (User-Agent,
`Retry-After`, `Challenge`) over an `HttpTransport` (`SttpTransport` for acquisition, `JavaHttpTransport`
for media and catalogues), `OutboundTls`, `Polite`, `FetchChain` + `OaFetcher` (the outcome now names the
fetcher that found), `WorkLookup` (a DOI's metadata and the `WorkKey` an ingest asks for), and how the host
reads a plugin's collection (`PluginMedia`, incl. the licence judgement `refusal`), catalogue
(`PluginEvents`, incl. the paging loop `readPart`) and AI provider (`PluginAi`). The server keeps thin
wrappers (`SttpHttp`, `PluginCollection`, `PluginAiClient`). Host code logs through `HostLog` (scribe by
default) so a test captures the lines.

`modules/plugin-testkit` (`com.histmersion::histmersion-plugin-testkit`, package
`com.histmersion.plugin.testkit`) depends on plugin-host only. `server/pluginRepo` writes it into the
install's `maven/` with `histmersion-plugin-host` and `histmersion-core` (jar, sources, POM each);
everything else comes from Maven Central through their POMs. A plugin uses it as `//> using test.dep`
(`plugin new` writes the line).

**API.** `PluginHarness.load(id, config: String | Path, http, secrets, env, histmersionConf)` →
`Either[String, PluginHarness]` (Left = the server's own message: no services file, no defaults resource,
an unknown or mistyped setting, a failed `register`, disabled). Runners: `fetch(work, saveTo, legalOnly,
allowUnauthorised): FetchReport` (`FetchOutcome.Found/Miss/Refused`, per-fetcher answers, licence, log,
requests), `searchMedia(collection, place): MediaReport`, `readCatalogue(catalogue, query):
CatalogueReport`, `aiTurn(provider, request, http): AiReport`, `resolveSecret(vault, ref)`,
`workFromDoi(doi)` / `WorkKey.fromDoi(doi, harness)`. `ScriptedAiHttp` answers an AI provider and keeps
its requests (credential headers masked); `PluginHarness.liveAiHttp` is the server's own client.

**HTTP modes.** `HttpMode.Live | Record(dir) | Replay(dir) | Stub(answers)`; whatever the mode, the
request first goes through the server's client, so only the wire differs. A recording is
`NNN-<host>.http` (method, URL, status, an allowlist of headers, `body: <file>` or `error: …`) plus the
body file; never written: `Authorization`, cookies, unlisted headers, any secret value the harness knows
(test secrets, values of `env:`/`value:` references in the config), and the contact e-mail (the testkit
blanks `acquire.contact-email`: requests are anonymous, so a recording replays on any machine). Replay
serves exchanges per URL in recorded order, skips every wait, and fails the runner with
`UnrecordedRequest` naming the URLs. `HttpMode.fromEnv(dir)`: `HISTMERSION_PLUGIN_HTTP`, else replay when
`dir` holds recordings, else live with `HISTMERSION_PLUGIN_LIVE`, else `Left` (skip).

**Depth per kind.** Fetch: the whole chain (legal gate, exception → miss, PDF check, memo, log). Media:
search through the collection's polite client, the host's licence reading and allowlist, `complete` and
the second judgement; place matching (needs the map) is not run. Catalogues: parts, paging, record checks,
the server's dating. AI: check then one guarded call (the tool loop belongs to sttp-ai on the server).
Vaults: the host's `resolve`. There is no download size limit on the acquisition path to mirror (media
renditions have `media.max-bytes`, applied at download, which the testkit does not do).

**Contract.** The testkit's public surface (everything outside `testkit.internal`) is listed in
`modules/plugin-testkit/plugin-testkit-api.txt`; `TestkitApiSuite` fails when it changes
(`HISTMERSION_UPDATE_TESTKIT_API=1` rewrites it), as `PluginPlatformSuite` does for the platform.

**Not built: `histmersion plugin try`.** The CLI is a native binary that must stay a pure HTTP client of
core; running a plugin jar needs a JVM with the testkit on its class path. A test written with the
harness does the same job from the plugin's own project, so the command was left out.

