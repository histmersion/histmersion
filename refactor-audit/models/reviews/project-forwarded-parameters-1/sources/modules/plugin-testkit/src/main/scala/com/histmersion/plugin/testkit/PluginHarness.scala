package com.histmersion.plugin.testkit

import hearth.kindlings.optics.syntax.*
import com.histmersion.config.{AppConfig, LoadedConfig}
import com.histmersion.api.plugins.PluginState
import com.histmersion.domain.media.imports.LicencePolicy
import com.histmersion.pluginhost.allowed
import com.histmersion.plugin.*
import com.histmersion.pluginhost.{
  upstreamSettings,
  FetchChain,
  FirstPartySettings,
  HostLog,
  HostServices,
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
    val id: PluginHarness.Id,
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
    val warnings: List[PluginHarness.Warnings]
):

  private val hostLog: HostLog = lines.host
  private def sleep: com.histmersion.plugin.testkit.PluginHarness.SleepResultArg1 => Unit = if mode.live then ms => Thread.sleep(ms) else _ => ()

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
  def gazetteerSources: List[com.histmersion.plugin.testkit.PluginHarness.GazetteerSourcesResult] = registries.gazetteerSources.keys.toList

  /** Every line logged since the plugin was loaded. */
  def log: List[LogLine] = lines.all

  /** Every request made since the plugin was loaded, `GET <url>`, with secrets shown as `{secret}`. */
  def requests: List[com.histmersion.plugin.testkit.PluginHarness.RequestsResult] = wire.requests

  /** The plugin's `HttpMode`. */
  def http: HttpMode = mode

  /** Runs `body`, then fails the test if it asked a replay for something not recorded. */
  private def run[A](body: => A): (A, List[LogLine], List[com.histmersion.plugin.testkit.PluginHarness.RunResultPart3]) =
    val (logFrom, askedFrom) = (lines.all.size, wire.requests.size)
    val result = body
    wire.check()
    (result, lines.all.drop(logFrom), wire.requests.drop(askedFrom))

  /** A work's identifiers and metadata by its DOI, as an ingest looks them up before it asks the chain: OpenAlex, else
    * Crossref; its title, authors and year filled in. These requests go through the harness's HTTP too, so they are
    * recorded and replayed with the rest. The contact e-mail of the user's config is never sent from a test (nor
    * recorded): the lookups are anonymous.
    */
  def workFromDoi(doi: com.histmersion.plugin.testkit.PluginHarness.WorkFromDoiDoi): Either[com.histmersion.plugin.testkit.PluginHarness.WorkFromDoiResultPart1, WorkKey] =
    val clean = doi.trim.toLowerCase(java.util.Locale.ROOT).replaceFirst("^(https?://)?(dx\\.)?doi\\.org/", "")
    def json(url: com.histmersion.plugin.testkit.PluginHarness.WorkFromDoiJsonUrl) = acquisition
      .bytes(url)
      .flatMap(b =>
        io.circe.parser.parse(new String(b, UTF_8)).left.map(e => s"$url returned unparseable JSON: ${e.getMessage}")
      )
    run(
      WorkLookup
        .meta(host.acquire.upstreamSettings, json, hostLog.plugin)(clean)
        .map(w => WorkLookup.key(Some(clean), None, Some(w)))
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
      legalOnly: com.histmersion.plugin.testkit.PluginHarness.FetchLegalOnly = false,
      allowUnauthorised: com.histmersion.plugin.testkit.PluginHarness.FetchAllowUnauthorised = false
  ): FetchReport =
    val (outcome, logged, asked) = run {
      val (chain, notes) = FetchChain.of(registries.sourceFetchers, Nil, allowUnauthorised, hostLog, Set(id))
      notes.foreach(hostLog.info)
      chain.fetchWorkExplained(work, legalOnly)
    }
    val answers = outcome.answers.map(a => FetcherAnswer(a.fetcher, a.kind == FetchChain.Answer.Kind.Refused, a.why))
    val result = outcome.file match
      case Right(file) =>
        val fetcher = outcome.foundBy.getOrElse("?")
        FetchOutcome.Found(fetcher, file, saveTo.map(dir => PluginHarness.save(dir, fetcher, work, file)))
      case Left(noCopy) =>
        val refused = answers.filter(_.refused)
        if refused.nonEmpty then FetchOutcome.Refused(refused.map(a => s"${a.fetcher}: ${a.why}").mkString("; "))
        else if answers.nonEmpty then FetchOutcome.Miss(answers.map(a => s"${a.fetcher}: ${a.why}").mkString("; "))
        else
          FetchOutcome.Miss(if registries.sourceFetchers.isEmpty then "the plugin registered no source fetcher"
          else noCopy)
    FetchReport(result, answers, logged, asked)

  /** One search of the plugin's media collection `collection` for `place`, through the collection's polite client (its
    * pace, the server's User-Agent and patience), each record judged as the server's import judges it — its licence
    * read from the statement and checked against `media.collections`'s allowlist for it — and each record the server
    * would keep completed (`complete`) and judged again. Tying a record to a place on the map is the server's and needs
    * the map: not run here.
    */
  def searchMedia(collection: com.histmersion.plugin.testkit.PluginHarness.SearchMediaCollection, place: MediaPlace): MediaReport =
    registries.mediaCollections.get(collection) match
      case None      => MediaReport(Nil, Some(s"the plugin registered no media collection '$collection'"), Nil, Nil)
      case Some(col) =>
        val ((verdicts, failure), logged, asked) = run {
          val polite = PluginMedia.polite(
            wire.transport(JavaHttpTransport(host.media.timeoutSeconds)),
            host.media,
            col,
            sleep,
            hostLog
          )
          val http = PluginMedia.http(polite)
          val allow = host.media.collections.allowed(collection)
          PluginMedia.guard(col.search(place, http)) match
            case Left(why)    => (Nil, Some(why))
            case Right(items) => (items.map(judge(collection, col, http, allow)), None)
        }
        MediaReport(verdicts, failure, logged, asked)

  private def judge(name: com.histmersion.plugin.testkit.PluginHarness.JudgeName, col: MediaCollection, http: Plugin.Http, allow: LicencePolicy)(
      answered: Either[com.histmersion.plugin.testkit.PluginHarness.JudgeAnsweredPart1, MediaItem]
  ): MediaVerdict =
    answered match
      case Left(why)   => MediaVerdict(why, None, None, Some(why))
      case Right(item) =>
        val rec = PluginMedia.record(item)
        PluginMedia.refusal(rec, allow, name) match
          case Some(why) => MediaVerdict(rec.key, Some(item), rec.rights.toOption.map(_.licence.id.value), Some(why))
          case None      =>
            PluginMedia.guard(col.complete(item, http)) match
              case Left(why) =>
                MediaVerdict(rec.key, Some(item), rec.rights.toOption.map(_.licence.id.value), Some(why))
              case Right(full) =>
                val completed = PluginMedia.record(full)
                val again = PluginMedia
                  .refusal(completed, allow, name)
                  .map(why => if why.startsWith("licence") then "licence not allowed on the full record" else why)
                MediaVerdict(completed.key, Some(full), completed.rights.toOption.map(_.licence.id.value), again)

  /** One import of the plugin's event catalogue `catalogue`: its parts for `query`, each read page by page through the
    * catalogue's polite client, each record checked and dated as the server stores it (a part is read once per run; the
    * server also skips parts an earlier import read).
    */
  def readCatalogue(catalogue: com.histmersion.plugin.testkit.PluginHarness.ReadCatalogueCatalogue, query: CatalogueQuery): CatalogueReport =
    registries.eventCatalogues.get(catalogue) match
      case None =>
        CatalogueReport(
          Nil,
          Nil,
          Nil,
          Nil,
          Nil,
          Some(s"the plugin registered no event catalogue '$catalogue'"),
          Nil,
          Nil
        )
      case Some(cat) =>
        val pages = List.newBuilder[(String, Either[String, Int])]
        val accepted = List.newBuilder[AcceptedEvent]
        val refused = List.newBuilder[String]
        val leftOut = List.newBuilder[String]
        val ((parts, failure), logged, asked) = run {
          val interval = PluginEvents.safe(cat.requestIntervalMs).getOrElse(1000)
          val polite = PluginEvents.polite(
            wire.transport(JavaHttpTransport(host.events.timeoutSeconds)),
            host.media,
            interval,
            sleep,
            hostLog
          )
          val http = PluginEvents.http(polite)
          val limits = PluginEvents.limits(Some(cat), java.time.Year.now.getValue.toLong)
          // the parts read in this run: a page naming one again is not followed
          val read = scala.collection.mutable.Set.empty[String]
          PluginEvents.guard(Right(cat.parts(query))) match
            case Left(why)    => (Nil, Some(why))
            case Right(parts) =>
              parts.distinct.foreach { first =>
                PluginEvents.readPart(cat, first, query, http, read.contains, false) { (part, got) =>
                  read += part
                  pages += part -> got.map(_.events.size)
                  got.toOption.toList
                    .flatMap(_.events)
                    .foreach(e =>
                      PluginEvents.record(catalogue, e) match
                        case Left(why)                => refused += s"$part: $why"
                        case Right(Kept(r, warnings)) =>
                          leftOut ++= warnings.map(w => s"$part: $w")
                          val d = PluginEvents.derive(r, limits)
                          val ts = d.timeSpan
                          accepted += AcceptedEvent(
                            e,
                            d.kind,
                            ts.notBefore.flatMap(SpiValues.year),
                            ts.beginBy.flatMap(SpiValues.year),
                            ts.endFrom.flatMap(SpiValues.year),
                            ts.notAfter.flatMap(SpiValues.year)
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
  def readGazetteer(name: com.histmersion.plugin.testkit.PluginHarness.ReadGazetteerName): GazetteerReport =
    registries.gazetteerSources.get(name) match
      case None      => GazetteerReport(Nil, Nil, Some(s"the plugin registered no gazetteer source '$name'"), Nil, Nil)
      case Some(src) =>
        val (got, logged, asked) = run {
          val http = PluginGazetteers.http(wire.transport(PluginGazetteers.transport(host.places)), hostLog)
          PluginGazetteers.guard(src.fetch(http)).map { places =>
            val credit = PluginGazetteers.guard(Right(src.rights.credit)).getOrElse("")
            places.map(p => PluginGazetteers.input(p, credit).map(in => AcceptedPlace(p, in.place.source)))
          }
        }
        got match
          case Left(why)   => GazetteerReport(Nil, Nil, Some(why), logged, asked)
          case Right(read) =>
            GazetteerReport(
              read.collect { case Right(a) => a },
              read.collect { case Left(w) => w },
              None,
              logged,
              asked
            )

  /** One model call to the plugin's AI provider `provider`, as the server's agents make it: the provider's check of
    * `request.model` first, then the call. `http` is what the provider sends through — [[ScriptedAiHttp]] in a test,
    * [[PluginHarness.liveAiHttp]] (the server's own client) to reach the real provider. (The server also refuses a
    * provider that spends the person's own plan while it listens beyond loopback; a test is on loopback.)
    */
  def aiTurn(provider: com.histmersion.plugin.testkit.PluginHarness.AiTurnProvider, request: AiTurn.Request, http: AiHttp = ScriptedAiHttp()): AiReport =
    registries.aiProviders.get(provider) match
      case None    => AiReport(Left(s"the plugin registered no AI provider '$provider'"), None, Nil)
      case Some(p) =>
        val ((check, turn), logged, _) = run {
          val check = PluginAi.check(p, request.model)
          (check, check.toOption.map(_ => PluginAi.turn(p, request, http)))
        }
        AiReport(check, turn, logged)

  /** `<vault>:<ref>` resolved by the plugin's secret vault `vault`, as the server resolves a secret reference in any
    * plugin's config.
    */
  def resolveSecret(vault: com.histmersion.plugin.testkit.PluginHarness.ResolveSecretVault, ref: com.histmersion.plugin.testkit.PluginHarness.ResolveSecretRef): Either[com.histmersion.plugin.testkit.PluginHarness.ResolveSecretResultPart1, com.histmersion.plugin.testkit.PluginHarness.ResolveSecretResultPart2] =
    PluginHost.resolve(Secret.Ref.Vault(vault, ref), env, registries.secretVaults)

object PluginHarness:

  /** The id of the stand-in vault plugin that serves a test's `secrets` (never one of the plugin's). */
  type WorkFromDoiJsonUrl = WorkFromDoiJsonUrl.T
  object WorkFromDoiJsonUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegisterSleepArg1 = RegisterSleepArg1.T
  object RegisterSleepArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Warnings = Warnings.T
  object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SleepResultArg1 = SleepResultArg1.T
  object SleepResultArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GazetteerSourcesResult = GazetteerSourcesResult.T
  object GazetteerSourcesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestsResult = RequestsResult.T
  object RequestsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResultPart3 = RunResultPart3.T
  object RunResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorkFromDoiResultPart1 = WorkFromDoiResultPart1.T
  object WorkFromDoiResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorkFromDoiDoi = WorkFromDoiDoi.T
  object WorkFromDoiDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchLegalOnly = FetchLegalOnly.T
  object FetchLegalOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchAllowUnauthorised = FetchAllowUnauthorised.T
  object FetchAllowUnauthorised extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchMediaCollection = SearchMediaCollection.T
  object SearchMediaCollection extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JudgeName = JudgeName.T
  object JudgeName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JudgeAnsweredPart1 = JudgeAnsweredPart1.T
  object JudgeAnsweredPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadCatalogueCatalogue = ReadCatalogueCatalogue.T
  object ReadCatalogueCatalogue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadGazetteerName = ReadGazetteerName.T
  object ReadGazetteerName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AiTurnProvider = AiTurnProvider.T
  object AiTurnProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveSecretResultPart1 = ResolveSecretResultPart1.T
  object ResolveSecretResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveSecretResultPart2 = ResolveSecretResultPart2.T
  object ResolveSecretResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveSecretVault = ResolveSecretVault.T
  object ResolveSecretVault extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveSecretRef = ResolveSecretRef.T
  object ResolveSecretRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadResultPart1 = LoadResultPart1.T
  object LoadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadId = _root_.com.histmersion.plugin.testkit.PluginHarness.DiscoverId
  val LoadId: _root_.com.histmersion.plugin.testkit.PluginHarness.DiscoverId.type = _root_.com.histmersion.plugin.testkit.PluginHarness.DiscoverId
  type LoadSecretsPart1 = _root_.com.histmersion.plugin.testkit.PluginHarness.TestVaultsSecretsPart1
  val LoadSecretsPart1: _root_.com.histmersion.plugin.testkit.PluginHarness.TestVaultsSecretsPart1.type = _root_.com.histmersion.plugin.testkit.PluginHarness.TestVaultsSecretsPart1
  type LoadSecretsPart2 = _root_.com.histmersion.plugin.testkit.PluginHarness.TestVaultsSecretsPart2
  val LoadSecretsPart2: _root_.com.histmersion.plugin.testkit.PluginHarness.TestVaultsSecretsPart2.type = _root_.com.histmersion.plugin.testkit.PluginHarness.TestVaultsSecretsPart2
  type LoadEnvPart1 = LoadEnvPart1.T
  object LoadEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadEnvPart2 = LoadEnvPart2.T
  object LoadEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfigTextResultPart1 = ConfigTextResultPart1.T
  object ConfigTextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfigTextResultPart2 = ConfigTextResultPart2.T
  object ConfigTextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverResultPart1 = DiscoverResultPart1.T
  object DiscoverResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverId = DiscoverId.T
  object DiscoverId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultsOfResultPart1 = DefaultsOfResultPart1.T
  object DefaultsOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultsOfResultPart2 = DefaultsOfResultPart2.T
  object DefaultsOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TestVaultsResultPart1 = TestVaultsResultPart1.T
  object TestVaultsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TestVaultsSecretsPart1 = TestVaultsSecretsPart1.T
  object TestVaultsSecretsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TestVaultsSecretsPart2 = TestVaultsSecretsPart2.T
  object TestVaultsSecretsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UserConfigResultPart1 = UserConfigResultPart1.T
  object UserConfigResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UserConfigId = _root_.com.histmersion.plugin.testkit.PluginHarness.DiscoverId
  val UserConfigId: _root_.com.histmersion.plugin.testkit.PluginHarness.DiscoverId.type = _root_.com.histmersion.plugin.testkit.PluginHarness.DiscoverId
  type UserConfigText = UserConfigText.T
  object UserConfigText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UserConfigWithVaults = UserConfigWithVaults.T
  object UserConfigWithVaults extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HostConfigResultPart1 = HostConfigResultPart1.T
  object HostConfigResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencedValuesResult = ReferencedValuesResult.T
  object ReferencedValuesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencedValuesText = ReferencedValuesText.T
  object ReferencedValuesText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencedValuesEnvPart1 = ReferencedValuesEnvPart1.T
  object ReferencedValuesEnvPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReferencedValuesEnvPart2 = ReferencedValuesEnvPart2.T
  object ReferencedValuesEnvPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegisterId = _root_.com.histmersion.plugin.testkit.PluginHarness.DiscoverId
  val RegisterId: _root_.com.histmersion.plugin.testkit.PluginHarness.DiscoverId.type = _root_.com.histmersion.plugin.testkit.PluginHarness.DiscoverId
  type SaveFetcher = SaveFetcher.T
  object SaveFetcher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val TestVaultsId = "testkit-vaults"

  /** The server's client for model calls, to run an AI provider against the real service. */
  def liveAiHttp: AiHttp = com.histmersion.pluginhost.HostAiHttp()

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
      id: com.histmersion.plugin.testkit.PluginHarness.LoadId,
      config: String | Path = "",
      http: HttpMode = HttpMode.Offline,
      secrets: Map[com.histmersion.plugin.testkit.PluginHarness.LoadSecretsPart1, com.histmersion.plugin.testkit.PluginHarness.LoadSecretsPart2] = Map.empty,
      env: Map[com.histmersion.plugin.testkit.PluginHarness.LoadEnvPart1, com.histmersion.plugin.testkit.PluginHarness.LoadEnvPart2] = sys.env,
      histmersionConf: Option[Path] = None
  ): Either[com.histmersion.plugin.testkit.PluginHarness.LoadResultPart1, PluginHarness] =
    val loader = Option(Thread.currentThread.getContextClassLoader).getOrElse(getClass.getClassLoader)
    val envSecrets = secrets.collect { case (k, v) if k.startsWith(s"${Secret.Ref.EnvScheme}:") => k.drop(4).trim -> v }
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
        Scrubber(secrets.values.toSet ++ referencedValues(text + "\n" + defaults, environment))
      )
    yield harness

  private def configText(config: String | Path): Either[com.histmersion.plugin.testkit.PluginHarness.ConfigTextResultPart1, com.histmersion.plugin.testkit.PluginHarness.ConfigTextResultPart2] = config match
    case p: Path =>
      if Files.isRegularFile(p) then Right(Files.readString(p, UTF_8)) else Left(s"no config file $p")
    case s: String => Right(s)

  /** The plugin with this id among the providers ServiceLoader finds, or what is missing. */
  private def discover(loader: ClassLoader, id: com.histmersion.plugin.testkit.PluginHarness.DiscoverId): Either[com.histmersion.plugin.testkit.PluginHarness.DiscoverResultPart1, Plugin] =
    val services = s"META-INF/services/${classOf[Plugin].getName}"
    if !loader.getResources(services).hasMoreElements then
      Left(
        s"no $services on the test class path: the server would find no plugin (the file names the plugin class, one per line)"
      )
    else
      val (found, errors) = PluginHost.discover(loader)
      found.filter(_.info.id == id) match
        case List(p) => Right(p)
        case Nil     =>
          Left(
            s"no plugin with the id '$id' on the test class path (found: ${found.map(_.info.id).mkString(", ")})" +
              errors.map("; " + _).mkString
          )
        case many =>
          Left(s"plugin id '$id' is claimed by ${many.size} plugins: ${many.map(_.getClass.getName).mkString(", ")}")

  /** The plugin's defaults, which must exist: without them the server knows none of its settings. */
  private def defaultsOf(p: Plugin): Either[com.histmersion.plugin.testkit.PluginHarness.DefaultsOfResultPart1, com.histmersion.plugin.testkit.PluginHarness.DefaultsOfResultPart2] =
    val loader = Option(p.getClass.getClassLoader).getOrElse(getClass.getClassLoader)
    Option(loader.getResource(p.defaultsResource)) match
      case None =>
        Left(
          s"${p.info.id}: no ${p.defaultsResource} beside the plugin class — the server would know none of " +
            "its settings and leave it disabled"
        )
      case Some(url) =>
        PluginHost
          .defaults(p)
          .flatMap(_ =>
            scala.util
              .Using(url.openStream)(in => new String(in.readAllBytes, UTF_8))
              .toEither
              .left
              .map(e => s"${p.defaultsResource}: ${e.getMessage}")
          )

  /** The stand-in vaults that serve the test's `<vault>:<ref>` secrets, if it has any. */
  private def testVaults(secrets: Map[com.histmersion.plugin.testkit.PluginHarness.TestVaultsSecretsPart1, com.histmersion.plugin.testkit.PluginHarness.TestVaultsSecretsPart2]): Either[com.histmersion.plugin.testkit.PluginHarness.TestVaultsResultPart1, Option[Plugin]] =
    val refs = secrets.toList
      .filterNot(_._1.startsWith(s"${Secret.Ref.EnvScheme}:"))
      .map((k, v) => Secret.Ref.parse(k).map(_ -> v))
    refs.collectFirst { case Left(e) => e } match
      case Some(e) => Left(s"secrets: $e")
      case None    =>
        val byVault = refs
          .collect { case Right((Secret.Ref.Vault(vault, ref), v)) => (vault, ref, v) }
          .groupBy(_._1)
          .view
          .mapValues(_.map(t => t._2 -> t._3).toMap)
          .toMap
        Right(Option.when(byVault.nonEmpty)(TestVaults(TestVaultsId, byVault)))

  private def userConfig(id: com.histmersion.plugin.testkit.PluginHarness.UserConfigId, text: com.histmersion.plugin.testkit.PluginHarness.UserConfigText, withVaults: com.histmersion.plugin.testkit.PluginHarness.UserConfigWithVaults): Either[com.histmersion.plugin.testkit.PluginHarness.UserConfigResultPart1, Config] =
    val path = s"${PluginHost.Root}.$id"
    scala.util
      .Try(ConfigFactory.parseString(text))
      .toEither
      .left
      .map(e => s"the config does not parse: ${e.getMessage}")
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
              ConfigValueFactory.fromIterable(List(id, TestVaultsId).asJava)
            )
      }

  /** The server's settings: its defaults and the environment's overrides (or a `histmersion.conf`), with the contact
    * e-mail left out so that no request of a test carries it — the settings a first-party plugin is supplied from too.
    */
  private def hostConfig(env: Config, file: Option[Path]): Either[com.histmersion.plugin.testkit.PluginHarness.HostConfigResultPart1, LoadedConfig] =
    AppConfig
      .loadWithUserFile(env, file)
      .map(l =>
        l.copy(
          config = l.config.modify(_.acquire.contactEmail).setTo(""),
          merged = l.merged.withValue(ContactPath, ConfigValueFactory.fromAnyRef(""))
        )
      )

  private val ContactPath = "histmersion.acquire.contact-email"

  /** The values of the `env:` and `value:` secret references a config text names. */
  private def referencedValues(text: com.histmersion.plugin.testkit.PluginHarness.ReferencedValuesText, env: Map[com.histmersion.plugin.testkit.PluginHarness.ReferencedValuesEnvPart1, com.histmersion.plugin.testkit.PluginHarness.ReferencedValuesEnvPart2]): Set[com.histmersion.plugin.testkit.PluginHarness.ReferencedValuesResult] =
    val named = "env:([A-Za-z_][A-Za-z0-9_]*)".r.findAllMatchIn(text).flatMap(m => env.get(m.group(1))).toSet
    val literal = "value:([^\"\\s,}\\]]+)".r.findAllMatchIn(text).map(_.group(1)).toSet
    named ++ literal

  private def register(
      id: com.histmersion.plugin.testkit.PluginHarness.RegisterId,
      plugin: Plugin,
      vaults: Option[Plugin],
      user: Config,
      env: Config,
      loaded: LoadedConfig,
      mode: HttpMode,
      scrub: Scrubber
  ): Either[com.histmersion.plugin.testkit.PluginHarness.RegisterResultPart1, PluginHarness] =
    val host = loaded.config
    val lines = Lines()
    val wire = Wire(mode, scrub)
    val sleep: com.histmersion.plugin.testkit.PluginHarness.RegisterSleepArg1 => Unit = if mode.live then ms => Thread.sleep(ms) else _ => ()
    val acquisition =
      PolicyHttp.acquisition(host, wire.transport(PolicyHttp.acquisitionTransport(host)), sleep, lines.host)
    val services = HostServices(() => acquisition.plugin, env, lines.plugin, files = new com.histmersion.pluginhost.DiskLocalFiles, processes = new com.histmersion.pluginhost.SystemProcesses)
    // a first-party plugin is supplied the server's own settings it reads, as the server supplies them
    val supplied = FirstPartySettings.hostView(loaded.merged, user, env, List(plugin))
    val generation = PluginHost.build(
      plugin :: vaults.toList,
      user,
      env,
      services,
      fallback = FirstPartySettings.fallback(_, supplied),
      firstParty = ReservedNames.firstPartyPlugins
    )
    val statuses = generation.report.plugins
    val failedVaults = statuses.find(s => s.id == TestVaultsId && s.state != PluginState.Registered.wire)
    statuses.find(_.id == id) match
      case None                                                => Left(s"$id: the server did not consider the plugin")
      case Some(st) if st.state == PluginState.Registered.wire =>
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
      case Some(st) if st.state == PluginState.Disabled.wire =>
        Left(s"$id is disabled: its config sets ${PluginHost.Root}.$id.enabled = false")
      case Some(st) =>
        Left(st.error.getOrElse(st.state) + failedVaults.flatMap(_.error).fold("")(e => s" (the test's secrets: $e)"))

  /** Where a found copy is written: `<dir>/<fetcher>-<identifier>.<extension>`. */
  private def save(dir: Path, fetcher: com.histmersion.plugin.testkit.PluginHarness.SaveFetcher, work: WorkKey, file: FetchedFile): Path =
    val ident =
      List(work.doi.map(_.value), work.isbn, work.arxiv, work.pmcid, work.pmid, work.archive).flatten.headOption
        .getOrElse("work")
    val ext = Option(file.extension).map(_.replaceAll("[^A-Za-z0-9]", "")).filter(_.nonEmpty).getOrElse("bin")
    Files.createDirectories(dir)
    val out = dir.resolve(s"$fetcher-${ident.replaceAll("[^A-Za-z0-9._-]", "_")}.$ext")
    Files.write(out, file.bytes)
