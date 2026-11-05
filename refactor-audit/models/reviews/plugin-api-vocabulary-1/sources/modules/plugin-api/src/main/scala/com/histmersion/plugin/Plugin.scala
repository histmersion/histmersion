package com.histmersion.plugin

import hearth.kindlings.sconfigderivation.ConfigReader
import scala.compiletime.deferred
import hearth.kindlings.sconfigderivation.{ConfigDecodingError, ConfigReader}
import java.util.Locale

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

  def info: com.histmersion.plugin.Plugin.Info

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
  def defaultsResource: com.histmersion.plugin.Plugin.DefaultsResourceResult = _root_.com.histmersion.plugin.Plugin.DefaultsResourceResult(s"META-INF/histmersion/plugins/${info.id}.conf")

  /** Called once per generation, only when enabled, after the host decoded the block into `config` and resolved every
    * [[Secret]] in it. Adds the plugin's contributions to `ctx`'s registries. The registries are STAGED: a `Left` (or
    * an exception) discards everything this plugin added.
    */
  def register(config: Config, ctx: com.histmersion.plugin.Plugin.Context): Either[com.histmersion.plugin.Plugin.RegisterResultPart1, Unit]

object Plugin:

  /** The readers a plugin's config derivation must see: an `${?ENV}` override always arrives as a HOCON STRING, so a
    * number or boolean setting has to accept a numeric or boolean string too — the same givens `core/Config.scala`
    * declares for Histmersion's own settings. A plugin imports them where it derives (`import
    * com.histmersion.plugin.Plugin.Config.given`); lexical-scope givens win over the companion defaults. Without them
    * an env override of a number fails with "Expected NUMBER but got STRING".
    */
  type DefaultsResourceResult = DefaultsResourceResult.T
  object DefaultsResourceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Config:
    type LenientName = LenientName.T
    object LenientName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LenientParseArg1 = LenientParseArg1.T
    object LenientParseArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    private def lenient[A](name: com.histmersion.plugin.Plugin.Config.LenientName, strict: ConfigReader[A], parse: com.histmersion.plugin.Plugin.Config.LenientParseArg1 => Option[A]): ConfigReader[A] =
      new ConfigReader[A]:
        def from(v: org.ekrich.config.ConfigValue) =
          strict
            .from(v)
            .orElse(
              ConfigReader.stringReader
                .from(v)
                .flatMap(s =>
                  parse(_root_.com.histmersion.plugin.Plugin.Config.LenientParseArg1(s.trim)).toRight(ConfigDecodingError.CannotConvert(Nil, s"'$s' is not a $name", None))
                )
            )

    given ConfigReader[Int] = lenient(_root_.com.histmersion.plugin.Plugin.Config.LenientName("int"), ConfigReader.intReader, (_).value.toIntOption)
    given ConfigReader[Long] = lenient(_root_.com.histmersion.plugin.Plugin.Config.LenientName("long"), ConfigReader.longReader, (_).value.toLongOption)
    given ConfigReader[Double] = lenient(_root_.com.histmersion.plugin.Plugin.Config.LenientName("double"), ConfigReader.doubleReader, (_).value.toDoubleOption)
    given ConfigReader[Boolean] = lenient(_root_.com.histmersion.plugin.Plugin.Config.LenientName("boolean"), ConfigReader.booleanReader, (_).value.toBooleanOption)

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
    def env(name: com.histmersion.plugin.Plugin.Context.EnvName): Option[com.histmersion.plugin.Plugin.Context.EnvResult]

    /** A secret's value. Every [[Secret]] inside the decoded config was resolved before [[Plugin.register]] was called,
      * so for those this is a lookup; another one is resolved now. Never log the value.
      */
    def secret(s: Secret): Either[com.histmersion.plugin.Plugin.Context.SecretResultPart1, com.histmersion.plugin.Plugin.Context.SecretResultPart2]

    /** Named secret backends: `<name>:<ref>` references are resolved by the vault registered as `name`. */
    def secretVaults: Registry[Secret.Vault]

    /** The acquisition chain: each fetcher under its own id, which is what `histmersion.acquire.chain` orders. A plugin
      * may add several (one per step). A host that has no acquisition chain refuses every addition — the default, so a
      * context written for an earlier extension point (a test's) still compiles.
      */
    def sourceFetchers: Registry[SourceFetcher] = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("this host has no acquisition chain"))

    /** The media collections `histmersion media import <id>` runs: each under its own id — the name the import is asked
      * for, and the provider its pictures are stored under. The host judges every licence and every match itself
      * ([[MediaCollection]]).
      */
    def mediaCollections: Registry[MediaCollection] = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("this host has no media import"))

    /** The event catalogues `histmersion events import <id>` reads, each under its own id — the import's name and the
      * catalogue its records are stored under.
      */
    def eventCatalogues: Registry[EventCatalogue] = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("this host has no event catalogues"))

    /** The AI providers the embedded agents call, each under its PROVIDER NAME — the part before the colon of a
      * `provider:model` reference. The host runs the tool loop, the tools, the validation and every budget; the
      * provider answers one model call at a time.
      */
    def aiProviders: Registry[AiProvider] = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("this host has no AI providers"))

    /** The host's budget for the metered upstream `upstream` (`core`: CORE's API), shared with the host's own calls to
      * it and kept for the whole server. An upstream the host keeps no budget for gets [[UpstreamQuota.Unlimited]] —
      * the default, so a context written before budgets existed still compiles.
      */
    def quota(upstream: com.histmersion.plugin.Plugin.Context.QuotaUpstream): UpstreamQuota = UpstreamQuota.Unlimited

    /** The host's reading of licence statements ([[LicenceReader]]), for a media collection that must decide on a
      * licence before it states it. A host that lends none reads every statement as unknown — the default, so a context
      * written before it existed still compiles.
      */
    def licences: LicenceReader = LicenceReader.Unread

    /** The gazetteer datasets `histmersion places fetch <id>` imports, each under its own id — the name the import is
      * asked for. A host that imports no gazetteers refuses every addition — the default, so a context written for an
      * earlier extension point still compiles.
      */
    def gazetteerSources: Registry[GazetteerSource] = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("this host has no gazetteer import"))

    /** The collections of sources the library searches and imports from, and may write back to, each under its own name
      * — the name `histmersion calibre …` or `histmersion archive …` runs it by ([[SourceCollection]]). A host that has
      * no library refuses every addition — the default, so a context written before collections existed still compiles.
      */
    def sourceCollections: Registry[SourceCollection] = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("this host has no source collections"))

  object Context:
    type EnvResult = EnvResult.T
    object EnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EnvName = EnvName.T
    object EnvName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SecretResultPart1 = SecretResultPart1.T
    object SecretResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SecretResultPart2 = SecretResultPart2.T
    object SecretResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type QuotaUpstream = QuotaUpstream.T
    object QuotaUpstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The host's HTTP client, as a plugin sees it. Failures are `Left(message)`, never exceptions. */
  trait Http:
    /** One GET under the host's outbound policy (its User-Agent, `Retry-After`, a slow-down asked again, a bot
      * challenge ending the request): the body of a 2xx answer, or why there is none.
      */
    def get(url: com.histmersion.plugin.Plugin.Http.GetUrl, headers: Map[com.histmersion.plugin.Plugin.Http.GetHeadersPart1, com.histmersion.plugin.Plugin.Http.GetHeadersPart2] = Map.empty): Either[com.histmersion.plugin.Plugin.Http.GetResultPart1, com.histmersion.plugin.Plugin.Http.GetResultPart2]

    /** One GET with the host's identity, answered as it came — its status and headers, a slow-down or an error answer
      * included — and never asked again: for an upstream whose own budget decides when to ask again (an
      * [[UpstreamQuota]] reads the answer), where a retry would spend what the budget is keeping. `Left` only when the
      * host could not talk to the upstream at all.
      *
      * A host that has only bodies answers through [[get]]: status 200 and no headers, or its `Left`.
      */
    def fetch(url: com.histmersion.plugin.Plugin.Http.FetchUrl, headers: Map[com.histmersion.plugin.Plugin.Http.FetchHeadersPart1, com.histmersion.plugin.Plugin.Http.FetchHeadersPart2] = Map.empty): Either[com.histmersion.plugin.Plugin.Http.FetchResultPart1, HttpResponse] =
      get(url, headers).map(HttpResponse(200, Map.empty, _))

  object Http:
    type GetResultPart2 = _root_.com.histmersion.plugin.Plugin.HttpResponse.Body
    val GetResultPart2: _root_.com.histmersion.plugin.Plugin.HttpResponse.Body.type = _root_.com.histmersion.plugin.Plugin.HttpResponse.Body

    type GetResultPart1 = GetResultPart1.T
    object GetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetUrl = GetUrl.T
    object GetUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetHeadersPart1 = GetHeadersPart1.T
    object GetHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetHeadersPart2 = GetHeadersPart2.T
    object GetHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FetchResultPart1 = _root_.com.histmersion.plugin.Plugin.Http.GetResultPart1
    val FetchResultPart1: _root_.com.histmersion.plugin.Plugin.Http.GetResultPart1.type = _root_.com.histmersion.plugin.Plugin.Http.GetResultPart1
    type FetchUrl = _root_.com.histmersion.plugin.Plugin.Http.GetUrl
    val FetchUrl: _root_.com.histmersion.plugin.Plugin.Http.GetUrl.type = _root_.com.histmersion.plugin.Plugin.Http.GetUrl
    type FetchHeadersPart1 = _root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart1
    val FetchHeadersPart1: _root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart1.type = _root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart1
    type FetchHeadersPart2 = _root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart2
    val FetchHeadersPart2: _root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart2.type = _root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart2

  /** One HTTP answer as it came: its status, its headers (names in lower case, the first value of each) and its body.
    *
    * @param status
    *   the HTTP status, a slow-down (`429`) or an error answer included
    * @param headers
    *   the answer's headers, by lower-case name
    * @param body
    *   the answer's bytes
    */
  final case class HttpResponse(status: HttpResponse.Status, headers: Map[HttpResponse.HeadersKey, HttpResponse.HeadersValue], body: HttpResponse.Body):
    /** A header by name, in any case. */
    def header(name: com.histmersion.plugin.Plugin.HttpResponse.HeaderName): Option[com.histmersion.plugin.Plugin.HttpResponse.HeaderResult] = headers.get(_root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersKey((name).value.toLowerCase(Locale.ROOT)))

  object HttpResponse:
    type HeaderResult = _root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersValue
    val HeaderResult: _root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersValue.type = _root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersValue
    type HeaderName = HeaderName.T
    object HeaderName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Body = Body.T
    object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeadersKey = HeadersKey.T
    object HeadersKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeadersValue = HeadersValue.T
    object HeadersValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Version = Version.T
    object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SpiVersion = SpiVersion.T
    object SpiVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The host's log, with the plugin's id on every line. */
  trait Log:
    def info(message: com.histmersion.plugin.Plugin.Log.InfoMessage): Unit
    def warn(message: com.histmersion.plugin.Plugin.Log.WarnMessage): Unit

  object Log:
    type InfoMessage = InfoMessage.T
    object InfoMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WarnMessage = WarnMessage.T
    object WarnMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
