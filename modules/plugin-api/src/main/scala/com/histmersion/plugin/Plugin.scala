package com.histmersion.plugin

import hearth.kindlings.sconfigderivation.ConfigReader
import scala.compiletime.deferred

/** The plugin SPI: everything a plugin author compiles against, and nothing else — no domain model, no database, no
  * server. The host (`com.histmersion.server.plugins`) owns discovery, config layering, secret resolution and the
  * registries' contents.
  */

/** The ONE ServiceLoader service (`META-INF/services/com.histmersion.plugin.Plugin`). A plugin may add to several parts
  * of the app at once; it does so in [[register]], through the context's registries.
  *
  * The provider is a public class with a no-argument constructor (ServiceLoader needs one; a Scala `object` has none),
  * and constructing it must be cheap and side-effect free: real work starts in [[register]], and only for an enabled
  * plugin.
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

  /** Path-dependent: the host decodes `histmersion.plugins.<id>` with THIS reader and hands the value to THIS plugin's
    * [[register]], so no cast is needed and the value cannot reach another plugin. A `deferred` given: a plugin whose
    * `Config` `derives ConfigReader` gets it filled in by the compiler; derive with
    * `import com.histmersion.plugin.PluginConfig.given` in scope (lenient numbers and booleans: an env override is
    * always a string).
    */
  given configReader: ConfigReader[Config] = deferred

  /** The plugin's defaults (HOCON, keys relative to its block), read through the plugin's class loader. Its own name
    * per plugin — never `reference.conf`, which every jar would claim. The file is also the plugin's type schema: every
    * key the plugin reads has a default in it (`null` for an optional value such as an optional [[Secret]]), and a key
    * the user sets that it lacks is reported as unknown. `enabled` is the host's key; absent, the plugin is disabled.
    */
  def defaultsResource: String = s"META-INF/histmersion/plugins/${info.id}.conf"

  /** Called once per generation, only when enabled, after the host decoded the block into `config` and resolved every
    * [[Secret]] in it. Adds the plugin's contributions to `ctx`'s registries. The registries are STAGED: a `Left` (or
    * an exception) discards everything this plugin added.
    */
  def register(config: Config, ctx: PluginContext): Either[String, Unit]
