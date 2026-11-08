package com.histmersion
package plugin.testkit

import hearth.kindlings.optics.syntax.*
import com.histmersion.config.{AppConfig, LoadedConfig}
import com.histmersion.api.plugins.{PluginState, PluginStatus}
import com.histmersion.domain.DeepTime
import com.histmersion.api.catalogue.CatalogRecord
import com.histmersion.domain.media.imports.MediaRecord
import com.histmersion.domain.media.imports.LicencePolicy
import com.histmersion.pluginhost.allowed
import com.histmersion.plugin.*
import com.histmersion.pluginhost.{
  upstreamSettings,
  FetchChain,
  FirstPartySettings,
  HostLog,
  HostServices,
  HttpTransport,
  JavaHttpTransport,
  Kept,
  PluginAi,
  PluginEvents,
  PluginGazetteers,
  PluginHost,
  PluginMedia,
  PolicyHttp,
  Registries,
  ReservedNames,
  SpiValues
}
import com.histmersion.domain.library.acquisition.WorkLookup
import org.ekrich.config.{Config, ConfigFactory, ConfigValueFactory}

import com.histmersion.plugin.testkit.internal.{Lines, Scrubber, TestVaults, Wire}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.config.MediaPluginCollectionsConfig
import com.histmersion.domain.Attribution
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.pluginhost.HostAiHttp
import com.histmersion.config.AcquireConfig
import com.histmersion.pluginhost.DiskLocalFiles
import com.histmersion.pluginhost.SystemProcesses

/** A plugin loaded as the Histmersion server loads it — without the server, its database or Postgres — and run as the
  * server runs it.
  *
  * [[PluginHarness.load]] finds the plugin through ServiceLoader on the test class path, layers the test's config over
  * the plugin's defaults resource, resolves its secrets and calls `register`, all with the server's own code: what
  * loads here loads in the server, and a refusal here is the server's own message. The runners then do what the server
  * does with each contribution — [[fetch]] runs the acquisition chain over the plugin's fetchers, [[searchMedia]] a
  * media collection's search, [[readCatalogue]] an event catalogue's import, [[aiTurn]] one model call,
  * [[resolveSecret]] a vault's lookup — and report what the server would have made of the answers.
  *
  * Every request goes through the server's HTTP client (its User-Agent, timeouts, TLS trust, its handling of
  * `Retry-After` and of bot challenges); [[HttpMode]] decides whether it then reaches the network, a recording or a
  * canned answer.
  *
  * {{{
  * class MyFetcherLiveSuite extends munit.FunSuite:
  *   test("finds and downloads a known open-access book"):
  *     val mode = HttpMode.fromEnv(Path.of("src/test/recordings/known-book"))
  *     assume(mode.isRight, mode.left.getOrElse(""))
  *     val harness = PluginHarness.load("my-plugin", "base-url = \"https://books.example\"",
  *       http = mode.toOption.get).fold(e => fail(e), identity)
  *     val work   = WorkKey.fromDoi("10.1234/abc", harness).fold(e => fail(e), identity)
  *     val report = harness.fetch(work, saveTo = Some(Path.of("target/fetched")))
  *     assert(report.outcome.isInstanceOf[FetchOutcome.Found], report.toString)
  * }}}
  */
final class PluginHarness private (
    val id: Plugin.Info.Id,
    /** The plugin as loaded. */
    val plugin: Plugin,
    registries: Registries,
    host: AppConfig,
    env: Config,
    mode: HttpMode,
    wire: Wire,
    lines: Lines,
    acquisition: PolicyHttp,
    /** What the server would have warned about while loading the plugin (a secret written into the config). */
    val warnings: List[PluginStatus.Warnings]
):

  private val hostLog: HostLog = lines.host
  private def nap(ms: Long): Unit = if mode.live then Thread.sleep(ms)

  /** What the plugin registered. */
  val contributions: Contributions =
    Contributions(
      registries.sourceFetchers.keys.toList,
      registries.mediaCollections.keys.toList,
      registries.eventCatalogues.keys.toList,
      registries.aiProviders.keys.toList,
      registries.secretVaults.keys.toList
    )

  /** The gazetteer sources the plugin registered, each under the name `places fetch` takes (`gazetteer-source:<name>`
    * in the server's list).
    */
  def gazetteerSources: List[Registries.GazetteerSourcesKey] = registries.gazetteerSources.keys.toList

  /** Every line logged since the plugin was loaded. */
  def log: List[LogLine] = lines.all

  /** Every request made since the plugin was loaded, `GET <url>`, with secrets shown as `{secret}`. */
  def requests: List[PluginHarness.Request] = wire.requests

  /** The plugin's `HttpMode`. */
  def http: HttpMode = mode

  /** Runs `body`, then fails the test if it asked a replay for something not recorded. */
  private def run[A](body: => A): (A, List[LogLine], List[PluginHarness.Request]) =
    val (logFrom, askedFrom) = (lines.all.size, wire.requests.size)
    val result = body
    wire.check()
    (result, lines.all.drop(logFrom), wire.requests.drop(askedFrom))

  /** A work's identifiers and metadata by its DOI, as an ingest looks them up before it asks the chain: OpenAlex, else
    * Crossref; its title, authors and year filled in. These requests go through the harness's HTTP too, so they are
    * recorded and replayed with the rest. The contact e-mail of the user's config is never sent from a test (nor
    * recorded): the lookups are anonymous.
    */
  def workFromDoi(doi: com.histmersion.domain.library.Doi): Either[PluginHarness.Problem, WorkKey] =
    val clean = doi.value.trim.toLowerCase(java.util.Locale.ROOT).replaceFirst("^(https?://)?(dx\\.)?doi\\.org/", "")
    def json(url: RepositoryLanding.Candidate.Url) = acquisition
      .bytes(HttpTransport.Url(url.value))
      .left
      .map(e => WorkLookup.Problem(e.value))
      .flatMap(b =>
        io.circe.parser
          .parse(new String(b.value, UTF_8))
          .left
          .map(e => WorkLookup.Problem(s"${url.value} returned unparseable JSON: ${e.getMessage}"))
      )
    run(
      WorkLookup
        .meta(host.acquire.upstreamSettings, json, hostLog.plugin)(Doi(clean))
        .map(w => WorkLookup.key(Some(Doi(clean)), None, Some(w)))
        .left
        .map(e => PluginHarness.Problem(e.value))
    )._1

  /** One fetch of `work` through the plugin's source fetchers, as the server's acquisition chain runs it. With
    * `saveTo`, a copy found is written into that directory (created when missing) as
    * `<fetcher>-<identifier>.<extension>`.
    *
    * @param legalOnly
    *   the legal path (a watcher's auto-ingest): an `Access.Unauthorised` fetcher never runs
    * @param allowUnauthorised
    *   `histmersion.acquire.allow-unauthorised`; off, as on a stock server
    */
  def fetch(
      work: WorkKey,
      saveTo: Option[Path] = None,
      legalOnly: FetchChain.LegalOnly = FetchChain.LegalOnly(false),
      allowUnauthorised: FetchChain.AllowUnauthorised = FetchChain.AllowUnauthorised(false)
  ): FetchReport =
    val (outcome, logged, asked) = run {
      val (chain, notes) = FetchChain.of(
        registries.sourceFetchers.map((k, v) => com.histmersion.util.plugins.Plugin.Id.assume(k.value) -> v),
        Nil,
        allowUnauthorised,
        hostLog,
        Set(com.histmersion.util.plugins.Plugin.Id.assume(id.value))
      )
      notes.foreach(n => hostLog.info(HostLog.Line(n.value)))
      chain.fetchWorkExplained(work, legalOnly)
    }
    val answers = outcome.answers.map(a => FetcherAnswer(a.fetcher, a.kind == FetchChain.Answer.Kind.Refused, a.why))
    val result = outcome.file match
      case Right(file) =>
        val fetcher = outcome.foundBy.getOrElse(FetchChain.Outcome.FoundBy("?"))
        FetchOutcome.Found(fetcher, file, saveTo.map(dir => PluginHarness.save(dir, fetcher, work, file)))
      case Left(noCopy) =>
        val refused = answers.filter(_.refused)
        if refused.nonEmpty then FetchOutcome.Refused(FetchOutcome.Refused.Why(refused.map(a => s"${a.fetcher}: ${a.why}").mkString("; ")))
        else if answers.nonEmpty then FetchOutcome.Miss(FetchOutcome.Miss.Why(answers.map(a => s"${a.fetcher}: ${a.why}").mkString("; ")))
        else
          FetchOutcome.Miss(
            FetchOutcome.Miss.Why(
              if registries.sourceFetchers.isEmpty then "the plugin registered no source fetcher"
              else noCopy.value
            )
          )
    FetchReport(result, answers, logged, asked)

  /** One search of the plugin's media collection `collection` for `place`, through the collection's polite client (its
    * pace, the server's User-Agent and patience), each record judged as the server's import judges it — its licence
    * read from the statement and checked against `media.collections`'s allowlist for it — and each record the server
    * would keep completed (`complete`) and judged again. Tying a record to a place on the map is the server's and needs
    * the map: not run here.
    */
  def searchMedia(collection: Registries.MediaCollectionsKey, place: MediaPlace): MediaReport =
    registries.mediaCollections.get(collection) match
      case None      => MediaReport(Nil, Some(MediaReport.Failure(s"the plugin registered no media collection '$collection'")), Nil, Nil)
      case Some(col) =>
        val ((verdicts, failure), logged, asked) = run {
          val polite = PluginMedia.polite(
            wire.transport(JavaHttpTransport(HttpTransport.TimeoutSeconds(host.media.timeoutSeconds.value))),
            host.media,
            col,
            ms => nap(ms.value),
            hostLog
          )
          val http = PluginMedia.http(polite)
          val allow = host.media.collections.allowed(MediaPluginCollectionsConfig.PerCollectionKey(collection.value))
          PluginMedia.guard(col.search(place, http)) match
            case Left(why)    => (Nil, Some(MediaReport.Failure(why.value)))
            case Right(items) => (items.map(judge(PluginMedia.Provider(collection.value), col, http, allow)), None)
        }
        MediaReport(verdicts, failure, logged, asked)

  private def judge(name: PluginMedia.Provider, col: MediaCollection, http: Plugin.Http, allow: LicencePolicy)(
      answered: Either[?, MediaItem]
  ): MediaVerdict =
    answered match
      case Left(why)   => MediaVerdict(MediaRecord.Key(s"$why"), None, None, Some(PluginMedia.Refusal(s"$why")))
      case Right(item) =>
        val rec = PluginMedia.record(item)
        PluginMedia.refusal(rec, allow, name) match
          case Some(why) => MediaVerdict(rec.key, Some(item), rec.rights.toOption.map(_.licence.id), Some(why))
          case None      =>
            PluginMedia.guard(col.complete(item, http)) match
              case Left(why) =>
                MediaVerdict(rec.key, Some(item), rec.rights.toOption.map(_.licence.id), Some(PluginMedia.Refusal(why.value)))
              case Right(full) =>
                val completed = PluginMedia.record(full)
                val again = PluginMedia
                  .refusal(completed, allow, name)
                  .map(why => if why.value.startsWith("licence") then PluginMedia.Refusal("licence not allowed on the full record") else why)
                MediaVerdict(completed.key, Some(full), completed.rights.toOption.map(_.licence.id), again)

  /** One import of the plugin's event catalogue `catalogue`: its parts for `query`, each read page by page through the
    * catalogue's polite client, each record checked and dated as the server stores it (a part is read once per run; the
    * server also skips parts an earlier import read).
    */
  def readCatalogue(catalogue: Registries.EventCataloguesKey, query: CatalogueQuery): CatalogueReport =
    registries.eventCatalogues.get(catalogue) match
      case None =>
        CatalogueReport(
          Nil,
          Nil,
          Nil,
          Nil,
          Nil,
          Some(CatalogueReport.Failure(s"the plugin registered no event catalogue '$catalogue'")),
          Nil,
          Nil
        )
      case Some(cat) =>
        val pages = List.newBuilder[(PluginEvents.PartName, Either[PluginEvents.Problem, CatalogueReport.Events])]
        val accepted = List.newBuilder[AcceptedEvent]
        val refused = List.newBuilder[CatalogueReport.Refused]
        val leftOut = List.newBuilder[CatalogueReport.LeftOut]
        val ((parts, failure), logged, asked) = run {
          val interval = PluginEvents.safe(cat.requestIntervalMs).map(i => PluginEvents.IntervalMs(i.value)).getOrElse(PluginEvents.IntervalMs(1000))
          val polite = PluginEvents.polite(
            wire.transport(JavaHttpTransport(HttpTransport.TimeoutSeconds(host.events.timeoutSeconds.value))),
            host.media,
            interval,
            ms => nap(ms.value),
            hostLog
          )
          val http = PluginEvents.http(polite)
          val limits = PluginEvents.limits(Some(cat), DeepTime.SignedYear(java.time.Year.now.getValue.toLong))
          // the parts read in this run: a page naming one again is not followed
          val read = scala.collection.mutable.Set.empty[PluginEvents.PartName]
          PluginEvents.guard(Right(cat.parts(query).map(p => PluginEvents.PartName(p.value)))) match
            case Left(why)    => (Nil, Some(CatalogueReport.Failure(why.value)))
            case Right(parts) =>
              parts.distinct.foreach { first =>
                PluginEvents.readPart(cat, first, query, http, read.contains, false) { (part, got) =>
                  read += part
                  pages += part -> got.map(p => CatalogueReport.Events(p.events.size))
                  got.toOption.toList
                    .flatMap(_.events)
                    .foreach(e =>
                      PluginEvents.record(CatalogRecord.Provider(catalogue.value), e) match
                        case Left(why)                => refused += CatalogueReport.Refused(s"$part: $why")
                        case Right(Kept(r, warnings)) =>
                          leftOut ++= warnings.map(w => CatalogueReport.LeftOut(s"$part: $w"))
                          val d = PluginEvents.derive(r, limits)
                          val ts = d.timeSpan
                          def year(y: Option[com.histmersion.api.catalogue.Year]) =
                            y.flatMap(v => SpiValues.year(DeepTime.SignedYear(v.value)))
                          accepted += AcceptedEvent(
                            e,
                            d.kind,
                            year(ts.notBefore),
                            year(ts.beginBy),
                            year(ts.endFrom),
                            year(ts.notAfter)
                          )
                    )
                }
              }
              (parts.distinct, None)
        }
        CatalogueReport(
          parts,
          pages.result(),
          accepted.result(),
          refused.result(),
          leftOut.result(),
          failure,
          logged,
          asked
        )

  /** One import of the plugin's gazetteer source `name`, as `places fetch` runs it: the download through the server's
    * gazetteer client (its User-Agent and `places.fetch-*` timeouts), each place checked and converted as the server
    * stores it — without the store.
    */
  def readGazetteer(name: Registries.GazetteerSourcesKey): GazetteerReport =
    registries.gazetteerSources.get(name) match
      case None      => GazetteerReport(Nil, Nil, Some(GazetteerReport.Failure(s"the plugin registered no gazetteer source '$name'")), Nil, Nil)
      case Some(src) =>
        val (got, logged, asked) = run {
          val http = PluginGazetteers.http(wire.transport(PluginGazetteers.transport(host.places)), hostLog)
          PluginGazetteers.guard(src.fetch(http)).map { places =>
            val credit = PluginGazetteers.guard(Right(Attribution.Credit(src.rights.credit.value))).getOrElse(Attribution.Credit(""))
            places.map(p => PluginGazetteers.input(p, credit).map(in => AcceptedPlace(p, in.place.source)))
          }
        }
        got match
          case Left(why)   => GazetteerReport(Nil, Nil, Some(GazetteerReport.Failure(why.value)), logged, asked)
          case Right(read) =>
            GazetteerReport(
              read.collect { case Right(a) => a },
              read.collect { case Left(w) => GazetteerReport.Refused(w.value) },
              None,
              logged,
              asked
            )

  /** One model call to the plugin's AI provider `provider`, as the server's agents make it: the provider's check of
    * `request.model` first, then the call. `http` is what the provider sends through — [[ScriptedAiHttp]] in a test,
    * [[PluginHarness.liveAiHttp]] (the server's own client) to reach the real provider. (The server also refuses a
    * provider that spends the person's own plan while it listens beyond loopback; a test is on loopback.)
    */
  def aiTurn(provider: Registries.AiProvidersKey, request: AiTurn.Request, http: AiHttp = ScriptedAiHttp()): AiReport =
    registries.aiProviders.get(provider) match
      case None    => AiReport(Left(PluginAi.Problem(s"the plugin registered no AI provider '$provider'")), None, Nil)
      case Some(p) =>
        val ((check, turn), logged, _) = run {
          val check = PluginAi.check(p, PluginAi.ModelName(request.model.value))
          (check, check.toOption.map(_ => PluginAi.turn(p, request, http)))
        }
        AiReport(check, turn, logged)

  /** `<vault>:<ref>` resolved by the plugin's secret vault `vault`, as the server resolves a secret reference in any
    * plugin's config.
    */
  def resolveSecret(vault: Secret.Ref.Vault.Value, ref: Secret.Ref.Vault.Ref): Either[PluginHost.Problem, PluginHost.SecretValue] =
    PluginHost.resolve(
      Secret.Ref.Vault(vault, ref),
      env,
      registries.secretVaults.iterator.map((k, v) => Secret.Ref.Vault.Value(k.value) -> v).toMap
    )

object PluginHarness:
  /** Why a harness could not be built or a lookup could not be made. */
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]

  /** One request made through the harness: `GET <url>`, with secrets shown as `{secret}`. */
  type Request = Request.T
  object Request extends RefinedType[String, Pure]

  /** The id of the stand-in vault plugin that serves a test's `secrets` (never one of the plugin's). */
  val TestVaultsId: Plugin.Info.Id = Plugin.Info.Id("testkit-vaults")

  /** The server's client for model calls, to run an AI provider against the real service. */
  def liveAiHttp: AiHttp = HostAiHttp()

  /** Load the plugin `id` from the test class path as the server would, and register it.
    *
    * @param config
    *   the plugin's settings, HOCON: either its block's keys (`base-url = "…"`) or the whole
    *   `histmersion.plugins.<id> { … }` as in `histmersion.conf`, or a file holding either. Layered over the plugin's
    *   defaults resource and type-checked against it as the server does; `enabled = true` unless the config says
    *   otherwise
    * @param http
    *   where requests go (offline by default: every URL answers 404)
    * @param secrets
    *   the values of secret references, by reference: `"keepassxc:Portal/token" -> "…"` is served by a stand-in vault
    *   named `keepassxc`, `"env:PORTAL_TOKEN" -> "…"` is added to `env`. Real vaults and keychains are never asked.
    *   Every value is kept out of recordings
    * @param env
    *   the environment the server would have read (`env:` secrets, `${?VAR}` overrides in the defaults, the server's
    *   own `HISTMERSION_*` settings): this process's by default
    * @param histmersionConf
    *   a `histmersion.conf` to read the server's own settings from (its User-Agent, timeouts, TLS trust, media
    *   allowlists); the defaults otherwise
    * @return
    *   the harness, or the server's own reason for not registering the plugin
    */
  def load(
      id: Plugin.Info.Id,
      config: String | Path = "",
      http: HttpMode = HttpMode.Offline,
      secrets: Map[Secret.Ref.ParseText, Secret.Vault.ResolveResultPart2] = Map.empty,
      env: Map[String, String] = sys.env,
      histmersionConf: Option[Path] = None
  ): Either[Problem, PluginHarness] =
    val loader = Option(Thread.currentThread.getContextClassLoader).getOrElse(getClass.getClassLoader)
    val envSecrets = secrets.collect { case (k, v) if k.value.startsWith(s"${Secret.Ref.EnvScheme}:") => k.value.drop(4).trim -> v.value }
    val environment = env ++ envSecrets
    val envConfig = ConfigValueFactory.fromMap(environment.asJava).toConfig
    for
      text <- configText(config)
      plugin <- discover(loader, id)
      defaults <- defaultsOf(plugin)
      vaults <- testVaults(secrets)
      user <- userConfig(id, text, vaults.isDefined)
      host <- hostConfig(envConfig, histmersionConf)
      harness <- register(
        id,
        plugin,
        vaults,
        user,
        envConfig,
        host,
        http,
        Scrubber(secrets.values.map(_.value).toSet ++ referencedValues(text + "\n" + defaults, environment))
      )
    yield harness

  private def configText(config: String | Path): Either[Problem, String] = config match
    case p: Path =>
      if Files.isRegularFile(p) then Right(Files.readString(p, UTF_8)) else Left(Problem(s"no config file $p"))
    case s: String => Right(s)

  /** The plugin with this id among the providers ServiceLoader finds, or what is missing. */
  private def discover(loader: ClassLoader, id: Plugin.Info.Id): Either[Problem, Plugin] =
    val services = s"META-INF/services/${classOf[Plugin].getName}"
    if !loader.getResources(services).hasMoreElements then
      Left(
        Problem(
          s"no $services on the test class path: the server would find no plugin (the file names the plugin class, one per line)"
        )
      )
    else
      val (found, errors) = PluginHost.discover(loader)
      found.filter(_.info.id == id) match
        case List(p) => Right(p)
        case Nil     =>
          Left(
            Problem(
              s"no plugin with the id '$id' on the test class path (found: ${found.map(_.info.id).mkString(", ")})" +
                errors.map("; " + _.value).mkString
            )
          )
        case many =>
          Left(
            Problem(
              s"plugin id '$id' is claimed by ${many.size} plugins: ${many.map(_.getClass.getName).mkString(", ")}"
            )
          )

  /** The plugin's defaults, which must exist: without them the server knows none of its settings. */
  private def defaultsOf(p: Plugin): Either[Problem, String] =
    val loader = Option(p.getClass.getClassLoader).getOrElse(getClass.getClassLoader)
    Option(loader.getResource(p.defaultsResource.value)) match
      case None =>
        Left(
          Problem(
            s"${p.info.id}: no ${p.defaultsResource} beside the plugin class — the server would know none of " +
              "its settings and leave it disabled"
          )
        )
      case Some(url) =>
        PluginHost
          .defaults(p)
          .left
          .map(e => Problem(e.value))
          .flatMap(_ =>
            scala.util
              .Using(url.openStream)(in => new String(in.readAllBytes, UTF_8))
              .toEither
              .left
              .map(e => Problem(s"${p.defaultsResource}: ${e.getMessage}"))
          )

  /** The stand-in vaults that serve the test's `<vault>:<ref>` secrets, if it has any. */
  private def testVaults(secrets: Map[Secret.Ref.ParseText, Secret.Vault.ResolveResultPart2]): Either[Problem, Option[Plugin]] =
    val refs = secrets.toList
      .filterNot(_._1.value.startsWith(s"${Secret.Ref.EnvScheme}:"))
      .map((k, v) => Secret.Ref.parse(k).map(_ -> v))
    refs.collectFirst { case Left(e) => e } match
      case Some(e) => Left(Problem(s"secrets: ${e.value}"))
      case None    =>
        val byVault = refs
          .collect { case Right((Secret.Ref.Vault(vault, ref), v)) => (vault, ref, v) }
          .groupBy(_._1)
          .view
          .mapValues(_.map(t => t._2 -> t._3).toMap)
          .toMap
        Right(Option.when(byVault.nonEmpty)(TestVaults(TestVaultsId, byVault)))

  private def userConfig(id: Plugin.Info.Id, text: String, withVaults: Boolean): Either[Problem, Config] =
    val path = s"${PluginHost.Root}.$id"
    scala.util
      .Try(ConfigFactory.parseString(text))
      .toEither
      .left
      .map(e => Problem(s"the config does not parse: ${e.getMessage}"))
      .map { parsed =>
        val block = if parsed.hasPath(path) then parsed.getConfig(path) else parsed
        val on = if block.hasPath("enabled") then block
        else block.withValue("enabled", ConfigValueFactory.fromAnyRef(java.lang.Boolean.TRUE))
        val own = on.atPath(path)
        if !withVaults then own
        else
          own
            .withValue(
              s"${PluginHost.Root}.$TestVaultsId.enabled",
              ConfigValueFactory.fromAnyRef(java.lang.Boolean.TRUE)
            )
            .withValue(
              s"${PluginHost.Root}.${PluginHost.OrderKey}",
              ConfigValueFactory.fromIterable(List(id.value, TestVaultsId.value).asJava)
            )
      }

  /** The server's settings: its defaults and the environment's overrides (or a `histmersion.conf`), with the contact
    * e-mail left out so that no request of a test carries it — the settings a first-party plugin is supplied from too.
    */
  private def hostConfig(env: Config, file: Option[Path]): Either[Problem, LoadedConfig] =
    AppConfig
      .loadWithUserFile(env, file)
      .left
      .map(e => Problem(e.value))
      .map(l =>
        l.copy(
          config = l.config.modify(_.acquire.contactEmail).setTo(AcquireConfig.ContactEmail("")),
          merged = l.merged.withValue(ContactPath, ConfigValueFactory.fromAnyRef(""))
        )
      )

  private val ContactPath = "histmersion.acquire.contact-email"

  /** The values of the `env:` and `value:` secret references a config text names. */
  private def referencedValues(text: String, env: Map[String, String]): Set[String] =
    val named = "env:([A-Za-z_][A-Za-z0-9_]*)".r.findAllMatchIn(text).flatMap(m => env.get(m.group(1))).toSet
    val literal = "value:([^\"\\s,}\\]]+)".r.findAllMatchIn(text).map(_.group(1)).toSet
    named ++ literal

  private def register(
      id: Plugin.Info.Id,
      plugin: Plugin,
      vaults: Option[Plugin],
      user: Config,
      env: Config,
      loaded: LoadedConfig,
      mode: HttpMode,
      scrub: Scrubber
  ): Either[Problem, PluginHarness] =
    val host = loaded.config
    val lines = Lines()
    val wire = Wire(mode, scrub)
    val acquisition =
      PolicyHttp.acquisition(host, wire.transport(PolicyHttp.acquisitionTransport(host)), ms => if mode.live then Thread.sleep(ms.value), lines.host)
    val services = HostServices(() => acquisition.plugin, env, lines.plugin, files = new DiskLocalFiles, processes = new SystemProcesses)
    // a first-party plugin is supplied the server's own settings it reads, as the server supplies them
    val supplied = FirstPartySettings.hostView(loaded.merged, user, env, List(plugin))
    val generation = PluginHost.build(
      plugin :: vaults.toList,
      user,
      env,
      services,
      fallback = FirstPartySettings.fallback(_, supplied),
      firstParty = id => ReservedNames.firstPartyPlugins.contains(ReservedNames.Name(id.value))
    )
    val statuses = generation.report.plugins
    val failedVaults = statuses.find(s => s.id.value == TestVaultsId.value && s.state.value != PluginState.Registered.wire.value)
    statuses.find(_.id.value == id.value) match
      case None                                                => Left(Problem(s"$id: the server did not consider the plugin"))
      case Some(st) if st.state.value == PluginState.Registered.wire.value =>
        Right(
          PluginHarness(
            id,
            plugin,
            generation.contributions.getOrElse(id, Registries.empty),
            host,
            env,
            mode,
            wire,
            lines,
            acquisition,
            st.warnings
          )
        )
      case Some(st) if st.state.value == PluginState.Disabled.wire.value =>
        Left(Problem(s"$id is disabled: its config sets ${PluginHost.Root}.$id.enabled = false"))
      case Some(st) =>
        Left(Problem(st.error.map(_.value).getOrElse(st.state.value) + failedVaults.flatMap(_.error).fold("")(e => s" (the test's secrets: $e)")))

  /** Where a found copy is written: `<dir>/<fetcher>-<identifier>.<extension>`. */
  private def save(dir: Path, fetcher: FetchChain.Outcome.FoundBy, work: WorkKey, file: FetchedFile): Path =
    val ident =
      List(work.doi.map(_.value), work.isbn.map(_.value), work.arxiv.map(_.value), work.pmcid.map(_.value), work.pmid.map(_.value), work.archive.map(_.value)).flatten.headOption
        .getOrElse("work")
    val ext = Option(file.extension.value).map(_.replaceAll("[^A-Za-z0-9]", "")).filter(_.nonEmpty).getOrElse("bin")
    Files.createDirectories(dir)
    val out = dir.resolve(s"$fetcher-${ident.replaceAll("[^A-Za-z0-9._-]", "_")}.$ext")
    Files.write(out, file.bytes.value)
