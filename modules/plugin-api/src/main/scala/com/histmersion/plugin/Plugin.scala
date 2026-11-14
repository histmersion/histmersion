package com.histmersion
package plugin

import com.histmersion.util.problem.Problem
import hearth.kindlings.sconfigderivation.ConfigReader
import scala.compiletime.deferred
import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}
import java.util.Locale
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  *   val info = Plugin.Info("portal", "0.1.0", Spi.Version)
  *   def register(c: PortalConfig, ctx: Plugin.Context) = …
  * }}}
  */
trait Plugin:
  /** The plugin's own settings, decoded by [[configReader]] from its block. */
  type Config

  def info: Plugin.Info

  /** Path-dependent: the host decodes `histmersion.plugins.<id>` with THIS reader and hands the value to THIS plugin's
    * [[register]], so no cast is needed and the value cannot reach another plugin. A `deferred` given: a plugin whose
    * `Config` `derives ConfigReader` gets it filled in by the compiler; derive with
    * `import com.histmersion.plugin.Plugin.Config.given` in scope (lenient numbers and booleans: an env override is
    * always a string).
    */
  given configReader: ConfigReader[Config] = deferred

  /** The plugin's defaults (HOCON, keys relative to its block), read through the plugin's class loader. Its own name
    * per plugin — never `reference.conf`, which every jar would claim. The file is also the plugin's type schema: every
    * key the plugin reads has a default in it (`null` for an optional value such as an optional [[Secret]]), and a key
    * the user sets that it lacks is reported as unknown. `enabled` is the host's key; absent, the plugin is disabled.
    */
  def defaultsResource: Plugin.DefaultsResource =
    Plugin.DefaultsResource(s"META-INF/histmersion/plugins/${info.id}.conf")

  /** Called once per generation, only when enabled, after the host decoded the block into `config` and resolved every
    * [[Secret]] in it. Adds the plugin's contributions to `ctx`'s registries. The registries are STAGED: a `Left` (or
    * an exception) discards everything this plugin added.
    */
  def register(config: Config, ctx: Plugin.Context): Either[Problem, Unit]

object Plugin:
  /** The readers a plugin's config derivation must see: an `${?ENV}` override always arrives as a HOCON STRING, so a
    * number or boolean setting has to accept a numeric or boolean string too — the same givens `core/Config.scala`
    * declares for Histmersion's own settings. A plugin imports them where it derives (`import
    * com.histmersion.plugin.Plugin.Config.given`); lexical-scope givens win over the companion defaults. Without them
    * an env override of a number fails with "Expected NUMBER but got STRING".
    */
  type DefaultsResource = DefaultsResource.T
  object DefaultsResource extends Newtype[String, Pure]

  object Config:
    type LenientName = LenientName.T
    object LenientName extends Newtype[String, Pure]
    type LenientText = LenientText.T
    object LenientText extends Newtype[String, Pure]

    private def lenient[A](
        name: LenientName,
        strict: ConfigReader[A],
        parse: LenientText => Option[A]
    ): ConfigReader[A] =
      new ConfigReader[A]:
        def from(v: org.ekrich.config.ConfigValue) =
          strict
            .from(v)
            .orElse(
              ConfigReader.stringReader
                .from(v)
                .flatMap(s =>
                  parse(LenientText(s.trim))
                    .toRight(ConfigDecodingError.CannotConvert(Nil, s"'$s' is not a $name", None))
                )
            )

    given ConfigReader[Int] = lenient(LenientName("int"), ConfigReader.intReader, (_).value.toIntOption)
    given ConfigReader[Long] = lenient(LenientName("long"), ConfigReader.longReader, (_).value.toLongOption)
    given ConfigReader[Double] = lenient(LenientName("double"), ConfigReader.doubleReader, (_).value.toDoubleOption)
    given ConfigReader[Boolean] = lenient(LenientName("boolean"), ConfigReader.booleanReader, (_).value.toBooleanOption)

    given newtypeReader
        : [T] => (mirror: RefinedType.Mirror[T]) => (base: ConfigReader[mirror.BaseType]) => ConfigReader[T] =
      base.emap(mirror.ops.either).asInstanceOf[ConfigReader[T]]

  /** What the host lends a plugin, and where it plugs in. Each registry is one extension point; a new extension point
    * is a new registry here, not a new service type, so existing plugin jars keep loading. (Seven: [[secretVaults]],
    * [[sourceFetchers]], [[mediaCollections]], [[eventCatalogues]], [[aiProviders]], [[gazetteerSources]] and
    * [[sourceCollections]]. Each one added after the first has a default that refuses every addition, so a context
    * written for an earlier extension point — a test's — still compiles, and adding them changed no `Spi.Version`.)
    */
  trait Context:
    /** The host's outbound HTTP — the one policy (User-Agent without personal data, Retry-After, bot challenges stop
      * the request). A plugin never opens its own sockets.
      */
    def files: LocalFiles = LocalFiles.Unavailable
    def processes: Processes = Processes.Unavailable
    def http: Http

    def log: Log

    /** The host's environment as it was read at startup (on the main thread: a native image reads it once). For what a
      * plugin needs to run a tool (`PATH`), never for credentials — those are [[Secret]]s in the config.
      */
    def env(name: EnvVar.Name): Option[EnvVar.Value]

    /** A secret's value. Every [[Secret]] inside the decoded config was resolved before [[Plugin.register]] was called,
      * so for those this is a lookup; another one is resolved now. Never log the value.
      */
    def secret(s: Secret): Either[Problem, Secret.Resolved]

    /** Named secret backends: `<name>:<ref>` references are resolved by the vault registered as `name`. */
    def secretVaults: Registry[Secret.Vault]

    /** The acquisition chain: each fetcher under its own id, which is what `histmersion.acquire.chain` orders. A plugin
      * may add several (one per step). A host that has no acquisition chain refuses every addition — the default, so a
      * context written for an earlier extension point (a test's) still compiles.
      */
    def sourceFetchers: Registry[SourceFetcher] =
      Registry.refusing(Problem.Config.Unavailable(Problem.Config.Capability.AcquisitionChain))

    /** The media collections `histmersion media import <id>` runs: each under its own id — the name the import is asked
      * for, and the provider its pictures are stored under. The host judges every licence and every match itself
      * ([[MediaCollection]]).
      */
    def mediaCollections: Registry[MediaCollection] =
      Registry.refusing(Problem.Config.Unavailable(Problem.Config.Capability.MediaImport))

    /** The event catalogues `histmersion events import <id>` reads, each under its own id — the import's name and the
      * catalogue its records are stored under.
      */
    def eventCatalogues: Registry[EventCatalogue] =
      Registry.refusing(Problem.Config.Unavailable(Problem.Config.Capability.EventCatalogues))

    /** The AI providers the embedded agents call, each under its PROVIDER NAME — the part before the colon of a
      * `provider:model` reference. The host runs the tool loop, the tools, the validation and every budget; the
      * provider answers one model call at a time.
      */
    def aiProviders: Registry[AiProvider] =
      Registry.refusing(Problem.Config.Unavailable(Problem.Config.Capability.AiProviders))

    /** The host's budget for the metered upstream `upstream` (`core`: CORE's API), shared with the host's own calls to
      * it and kept for the whole server. An upstream the host keeps no budget for gets [[UpstreamQuota.Unlimited]] —
      * the default, so a context written before budgets existed still compiles.
      */
    def quota(upstream: Context.QuotaUpstream): UpstreamQuota = UpstreamQuota.Unlimited

    /** The host's reading of licence statements ([[LicenceReader]]), for a media collection that must decide on a
      * licence before it states it. A host that lends none reads every statement as unknown — the default, so a context
      * written before it existed still compiles.
      */
    def licences: LicenceReader = LicenceReader.Unread

    /** The gazetteer datasets `histmersion places fetch <id>` imports, each under its own id — the name the import is
      * asked for. A host that imports no gazetteers refuses every addition — the default, so a context written for an
      * earlier extension point still compiles.
      */
    def gazetteerSources: Registry[GazetteerSource] =
      Registry.refusing(Problem.Config.Unavailable(Problem.Config.Capability.GazetteerImport))

    /** The collections of sources the library searches and imports from, and may write back to, each under its own name
      * — the name `histmersion calibre …` or `histmersion archive …` runs it by ([[SourceCollection]]). A host that has
      * no library refuses every addition — the default, so a context written before collections existed still compiles.
      */
    def sourceCollections: Registry[SourceCollection] =
      Registry.refusing(Problem.Config.Unavailable(Problem.Config.Capability.SourceCollections))

  object Context:
    type QuotaUpstream = QuotaUpstream.T
    object QuotaUpstream extends Newtype[String, Pure]

  /** The host's HTTP client, as a plugin sees it. A request that gave no answer to use is `Left` with the upstream's
    * problem ([[Problem.Upstream]]: the status it answered, a bot challenge, no answer at all), never an exception.
    */
  trait Http:
    /** One GET under the host's outbound policy (its User-Agent, `Retry-After`, a slow-down asked again, a bot
      * challenge ending the request): the body of a 2xx answer, or why there is none.
      */
    def get(
        url: Http.Url,
        headers: Map[Header.Name, Header.Value] = Map.empty
    ): Either[Problem, HttpResponse.Body]

    /** One GET with the host's identity, answered as it came — its status and headers, a slow-down or an error answer
      * included — and never asked again: for an upstream whose own budget decides when to ask again (an
      * [[UpstreamQuota]] reads the answer), where a retry would spend what the budget is keeping. `Left` only when the
      * host could not talk to the upstream at all.
      *
      * A host that has only bodies answers through [[get]]: status 200 and no headers, or its `Left`.
      */
    def fetch(
        url: Http.Url,
        headers: Map[Header.Name, Header.Value] = Map.empty
    ): Either[Problem, HttpResponse] =
      get(url, headers).map(HttpResponse(Problem.Upstream.Status(200), Map.empty, _))

  object Http:
    type Url = Url.T
    object Url extends Newtype[String, Pure]

  /** One HTTP answer as it came: its status, its headers (names in lower case, the first value of each) and its body.
    *
    * @param status
    *   the HTTP status, a slow-down (`429`) or an error answer included
    * @param headers
    *   the answer's headers, by lower-case name
    * @param body
    *   the answer's bytes
    */
  final case class HttpResponse(
      status: Problem.Upstream.Status,
      headers: Map[Header.Name, Header.Value],
      body: HttpResponse.Body
  ):
    /** A header by name, in any case. */
    def header(name: Header.Name): Option[Header.Value] =
      headers.get(Header.Name(name.value.toLowerCase(Locale.ROOT)))

  object HttpResponse:
    type Body = Body.T
    object Body extends Newtype[Array[Byte], Pure]

  /** @param id
    *   the config key (`histmersion.plugins.<id>`) and the name in logs and reports; kebab-case, `[a-z0-9][a-z0-9-]*`
    * @param version
    *   the plugin's own version, as reported
    * @param spiVersion
    *   the SPI generation the plugin was compiled against ([[Spi.Version]]); the host refuses any other
    */
  final case class Info(id: Info.Id, version: Info.Version, spiVersion: Info.SpiVersion)

  object Info:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Version = Version.T
    object Version extends Newtype[String, Pure]
    type SpiVersion = SpiVersion.T
    object SpiVersion extends Newtype[Int, Pure]

  /** The host's log, with the plugin's id on every line. */
  trait Log:
    def info(message: Log.Line): Unit
    def warn(message: Log.Line): Unit

  object Log:
    type Line = Line.T
    object Line extends Newtype[String, Pure]
