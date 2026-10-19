package com.histmersion.plugin

import hearth.kindlings.sconfigderivation.ConfigReader
import scala.compiletime.deferred

/** The plugin SPI (docs/research-plugins.md §2.2): everything a plugin author compiles against, and
  * nothing else — no domain model, no database, no server. The host (`com.histmersion.server.plugins`)
  * owns discovery, config layering, secret resolution and the registries' contents. */

/** @param id         the config key (`histmersion.plugins.<id>`) and the name in logs and reports;
  *                   kebab-case, `[a-z0-9][a-z0-9-]*`
  * @param version    the plugin's own version, as reported
  * @param spiVersion the SPI generation the plugin was compiled against ([[Spi.Version]]); the host
  *                   refuses any other */
final case class PluginInfo(id: String, version: String, spiVersion: Int)

object Spi:
  /** Bumped on an incompatible change to anything in this package. A new extension point (a new
    * registry on [[PluginContext]]) is compatible: existing plugins never call it. */
  val Version: Int = 1

/** The ONE ServiceLoader service (`META-INF/services/com.histmersion.plugin.Plugin`). A plugin may add
  * to several parts of the app at once; it does so in [[register]], through the context's registries.
  *
  * The provider is a public class with a no-argument constructor (ServiceLoader needs one; a Scala
  * `object` has none), and constructing it must be cheap and side-effect free: real work starts in
  * [[register]], and only for an enabled plugin.
  *
  * {{{
  * final case class PortalConfig(baseUrl: String, perHost: Int, token: Secret) derives ConfigReader
  *
  * final class PortalPlugin extends Plugin:
  *   type Config = PortalConfig
  *   val info = PluginInfo("portal", "0.1.0", Spi.Version)
  *   def register(c: PortalConfig, ctx: PluginContext) = …
  * }}}
  */
trait Plugin:
  /** The plugin's own settings, decoded by [[configReader]] from its block. */
  type Config

  def info: PluginInfo

  /** Path-dependent: the host decodes `histmersion.plugins.<id>` with THIS reader and hands the value
    * to THIS plugin's [[register]], so no cast is needed and the value cannot reach another plugin.
    * A `deferred` given: a plugin whose `Config` `derives ConfigReader` gets it filled in by the
    * compiler; derive with `import com.histmersion.plugin.PluginConfig.given` in scope (lenient numbers
    * and booleans: an env override is always a string). */
  given configReader: ConfigReader[Config] = deferred

  /** The plugin's defaults (HOCON, keys relative to its block), read through the plugin's class loader.
    * Its own name per plugin — never `reference.conf`, which every jar would claim. The file is also
    * the plugin's type schema: every key the plugin reads has a default in it (`null` for an optional
    * value such as an optional [[Secret]]), and a key the user sets that it lacks is reported as
    * unknown. `enabled` is the host's key; absent, the plugin is disabled. */
  def defaultsResource: String = s"META-INF/histmersion/plugins/${info.id}.conf"

  /** Called once per generation, only when enabled, after the host decoded the block into `config`
    * and resolved every [[Secret]] in it. Adds the plugin's contributions to `ctx`'s registries. The
    * registries are STAGED: a `Left` (or an exception) discards everything this plugin added. */
  def register(config: Config, ctx: PluginContext): Either[String, Unit]

/** What the host lends a plugin, and where it plugs in. Each registry is one extension point; a new
  * extension point is a new registry here, not a new service type, so existing plugin jars keep loading.
  * (Five: [[secretVaults]], [[sourceFetchers]], [[mediaCollections]], [[eventCatalogues]] and
  * [[aiProviders]]. Each one
  * added after the first has a default that refuses every addition, so a context written for an
  * earlier extension point — a test's — still compiles, and `Spi.Version` stayed 1.) */
trait PluginContext:
  /** The host's outbound HTTP — the one policy (User-Agent without personal data, Retry-After,
    * bot challenges stop the request). A plugin never opens its own sockets. */
  def http: PluginHttp

  def log: PluginLog

  /** The host's environment as it was read at startup (on the main thread: a native image reads it
    * once). For what a plugin needs to run a tool (`PATH`), never for credentials — those are
    * [[Secret]]s in the config. */
  def env(name: String): Option[String]

  /** A secret's value. Every [[Secret]] inside the decoded config was resolved before [[Plugin.register]]
    * was called, so for those this is a lookup; another one is resolved now. Never log the value. */
  def secret(s: Secret): Either[String, String]

  /** Named secret backends: `<name>:<ref>` references are resolved by the vault registered as `name`. */
  def secretVaults: Registry[SecretVault]

  /** The acquisition chain (docs/research-plugins.md §5): each fetcher under its own id, which is
    * what `histmersion.acquire.chain` orders. A plugin may add several (one per step). A host that
    * has no acquisition chain refuses every addition — the default, so a context written for an
    * earlier extension point (a test's) still compiles. */
  def sourceFetchers: Registry[SourceFetcher] = Registry.refusing("this host has no acquisition chain")

  /** The media collections `histmersion media import <id>` runs (docs/research-plugins.md §6): each under
    * its own id — the name the import is asked for, and the provider its pictures are stored under. The
    * host judges every licence and every match itself ([[MediaCollection]]). */
  def mediaCollections: Registry[MediaCollection] = Registry.refusing("this host has no media import")

  /** The event catalogues `histmersion events import <id>` reads, each under its own id — the import's
    * name and the catalogue its records are stored under. */
  def eventCatalogues: Registry[EventCatalogue] = Registry.refusing("this host has no event catalogues")

  /** The AI providers the embedded agents call (docs/research-plugins.md §22), each under its PROVIDER
    * NAME — the part before the colon of a `provider:model` reference. The host runs the tool loop, the
    * tools, the validation and every budget; the provider answers one model call at a time. */
  def aiProviders: Registry[AiProvider] = Registry.refusing("this host has no AI providers")

/** One extension point. `add` refuses an id already taken in this registry. */
trait Registry[A]:
  def add(id: String, a: A): Either[String, Unit]

object Registry:
  /** A registry that takes nothing, saying why. */
  def refusing[A](why: String): Registry[A] = new Registry[A]:
    def add(id: String, a: A) = Left(s"'$id': $why")

/** The host's HTTP client, as a plugin sees it. Failures are `Left(message)`, never exceptions. */
trait PluginHttp:
  def get(url: String, headers: Map[String, String] = Map.empty): Either[String, Array[Byte]]

/** The host's log, with the plugin's id on every line. */
trait PluginLog:
  def info(message: String): Unit
  def warn(message: String): Unit
