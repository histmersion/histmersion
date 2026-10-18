# Plugins: an SPI, typed config, native image, user plugins, local builds (research, 2026-10-18)

**Status:** research and design proposal for #2017, with two proof-of-concept spikes (§10). Nothing
here changes the shipped build. The spike code is in `spike/plugins/`, a standalone sbt build that
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
| OA acquisition | `server/acquire/Clients.scala` `OaFetch` | one class; a `LazyList` of steps: Unpaywall → CORE → OpenAlex OA → Europe PMC/PMC PDF → arXiv → repository landing pages (Zenodo API, `citation_pdf_url`, DataCite) → PMC JATS text → the unauthorised mirror (`acquire.unlicensed.enabled`, off). `fetchLegal` skips the unauthorised mirror | the steps are private methods sharing two lazy records (`OaRecords`, `PmcLookup`). They must be split before they can be plugins (§5.3) |
| archive.org | `ArchiveOrg` (`ArchiveClient`: search / info / download) | a separate surface (`histmersion archive …`), not in the DOI chain | a fetcher by identifier, not by DOI. The `WorkKey` needs an `archive` id (§5.1) |
| Outbound HTTP | `acquire/Http.scala` (`SttpHttp`), `media/MediaHttp.scala` (`Polite`), `core/HttpPolicy` | one policy (#1946): User-Agent without an e-mail, Retry-After, back-off, bot challenges never worked around | **the host service a plugin must use** (`PluginHttp`, §2) |
| Media | `server/media/Collections.scala` `trait Collection`; `MediaImporter.of` (a `match` over names) | Met, Cleveland, AIC, LoC, Open Context are `Collection`s. Wikidata, depicts and Pleiades are bespoke runs | `Collection` is already an SPI in all but name |
| Event catalogues | `core/EventProviders` (GVP, NOAA, Wikidata), `server/events/EventCatalogHandlers` | pure URL/parse objects; the handler switches on the catalogue | a plugin kind once the record type (`CatalogRecord`) is in the API |
| Gazetteer | `server/importers/GazetteerLoad` (`geonames`, `wikidata`, `tgn` pinned extracts; `lpf` directory), Pleiades/Natural Earth seeds, Cliopatria, PeriodO, AWMC, ChronOntology importers | data pipelines; extracts are pinned by sha256 | the `lpf` directory is already a no-code extension point |
| Config | `core/Config.scala`, `core/UserConfig.scala` (`ConfigLayers`) | sconfig + `derives ConfigReader` (Kindlings). Layers: env > `histmersion.conf` > `reference.conf`. Unknown keys warn. `typeCheck` names a mistyped key because "the derived readers do not say which key a wrong type is at". Lenient `Int/Long/Double/Boolean` givens accept numeric strings from env | the contract a plugin block must follow (§3); spike A hit both gotchas |
| Native build | `build.sbt` `nativeImage` (custom task), `slothPatch`, `checkNoLegacyNativeConfigs`; `scripts/bundle/server.sh` | `native-image --no-fallback -cp <sloth-patched jars> Main`; per-module `reachability-metadata.json` from the tracing agent | plugin jars join `-cp`; their metadata travels inside the jar |
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

Dependencies beyond what the host ships are **shaded into the jar**. The image has one class loader
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
6. **Build.** `native-image --no-fallback @kit/native-image.args -cp kit/*:plugin-set.jar:plugins/* … → bin/histmersion-server.new`.
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

Plugin config changes alone (`enabled = false`) need only a restart, not a relink.

## 8. Building the native image locally at setup

### 8.1 Toolchain

| need | macOS | Linux |
|---|---|---|
| C toolchain | Xcode Command Line Tools (`xcode-select --install`); GraalVM states "Native Image requires the Xcode command line tools", macOS 13+ and Xcode 14+ | `build-essential zlib1g-dev` (Debian/Ubuntu); `gcc glibc-devel zlib-devel libstdc++-static` (dnf); glibc ≥ 2.17, zlib ≥ 1.2.3, GCC ≥ 10.3 (GraalVM *Getting started on Linux*) |
| GraalVM | CE 25.0.1, the `.sdkmanrc` pin: a tarball from `graalvm/graalvm-ce-builds` with a pinned sha256, fetched by `curl` (no quarantine) into `~/Applications/Histmersion/graalvm` | same, `~/.local/share/histmersion/graalvm` |
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

- **Inputs are pinned:** GraalVM 25.0.1 (sha256), the kit's jars (sha256 in the kit manifest), the
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
- **(unverified)** Whether TCC honours a grant for an untrusted self-signed DR as it does for a
  trusted one. TCC matches the stored requirement, which here is a certificate hash, so it should,
  but confirming it needs a real TCC prompt. Phase 5 of §13 includes that check. Until then, keep
  the trust step as today's script has it.

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
- **TCC with an untrusted self-signed identity** (§9.2): unverified. Keep the trust step until it is
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
   - Verify TCC persistence with an untrusted identity, and drop `add-trusted-cert` if it holds.
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
- **D4 — open-access indexes:** (not answered; phase 2 keeps one `oa-index` plugin and can split later).
- **D5 — local build at setup:** (not answered explicitly; signing below assumes it is offered).
- **D6 — signing:** optional.
- **D7 — paid sources:** plugins with credentials are allowed. Credentials are passed as secret
  references (§3.7). Each plugin still implements only what its provider's terms permit (§11).
- **D8 — installing a plugin:** Maven coordinates are one way among several: drop the jar into the
  plugins directory; `plugin add <path>` (copied in); `plugin add <url>` (downloaded, checksum
  shown, optionally verified); `plugin add <group:artifact:version>` (resolved from Maven).
- **One service type** and the app context (§2.2), **in-place config reload** (§3.5) and the **hot
  component graph** with no global JDBC (§3.6) were decided the same day.
