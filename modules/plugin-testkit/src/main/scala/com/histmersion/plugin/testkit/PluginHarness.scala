package com.histmersion.plugin.testkit

import hearth.kindlings.optics.syntax.*
import com.histmersion.config.AppConfig
import com.histmersion.api.PluginState
import com.histmersion.pluginhost.allowed
import com.histmersion.plugin.*
import com.histmersion.pluginhost.{
  FetchChain,
  HostLog,
  HostServices,
  JavaHttpTransport,
  PluginAi,
  PluginEvents,
  PluginHost,
  PluginMedia,
  PolicyHttp,
  Registries,
  SpiValues,
  WorkLookup
}
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
    val id: String,
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
    val warnings: List[String]
):

  private val hostLog: HostLog = lines.host
  private def sleep: Long => Unit = if mode.live then ms => Thread.sleep(ms) else _ => ()

  /** What the plugin registered. */
  val contributions: Contributions =
    Contributions(
      registries.sourceFetchers.keys.toList,
      registries.mediaCollections.keys.toList,
      registries.eventCatalogues.keys.toList,
      registries.aiProviders.keys.toList,
      registries.secretVaults.keys.toList
    )

  /** Every line logged since the plugin was loaded. */
  def log: List[LogLine] = lines.all

  /** Every request made since the plugin was loaded, `GET <url>`, with secrets shown as `{secret}`. */
  def requests: List[String] = wire.requests

  /** The plugin's `HttpMode`. */
  def http: HttpMode = mode

  /** Runs `body`, then fails the test if it asked a replay for something not recorded. */
  private def run[A](body: => A): (A, List[LogLine], List[String]) =
    val (logFrom, askedFrom) = (lines.all.size, wire.requests.size)
    val result = body
    wire.check()
    (result, lines.all.drop(logFrom), wire.requests.drop(askedFrom))

  /** A work's identifiers and metadata by its DOI, as an ingest looks them up before it asks the chain: OpenAlex, else
    * Crossref; its title, authors and year filled in. These requests go through the harness's HTTP too, so they are
    * recorded and replayed with the rest. The contact e-mail of the user's config is never sent from a test (nor
    * recorded): the lookups are anonymous.
    */
  def workFromDoi(doi: String): Either[String, WorkKey] =
    val clean = doi.trim.toLowerCase(java.util.Locale.ROOT).replaceFirst("^(https?://)?(dx\\.)?doi\\.org/", "")
    def json(url: String) = acquisition
      .bytes(url)
      .flatMap(b =>
        io.circe.parser.parse(new String(b, UTF_8)).left.map(e => s"$url returned unparseable JSON: ${e.getMessage}")
      )
    run(WorkLookup.meta(host.acquire, json, hostLog)(clean).map(w => WorkLookup.key(Some(clean), None, Some(w))))._1

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
      legalOnly: Boolean = false,
      allowUnauthorised: Boolean = false
  ): FetchReport =
    val (outcome, logged, asked) = run {
      val (chain, notes) = FetchChain.of(registries.sourceFetchers, Nil, allowUnauthorised, hostLog)
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
  def searchMedia(collection: String, place: MediaPlace): MediaReport =
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

  private def judge(name: String, col: MediaCollection, http: PluginHttp, allow: Set[String])(
      answered: Either[String, MediaItem]
  ): MediaVerdict =
    answered match
      case Left(why)   => MediaVerdict(why, None, None, Some(why))
      case Right(item) =>
        val rec = PluginMedia.record(item)
        PluginMedia.refusal(rec, allow, name) match
          case Some(why) => MediaVerdict(rec.key, Some(item), rec.rights.toOption.map(_.licence.id), Some(why))
          case None      =>
            PluginMedia.guard(col.complete(item, http)) match
              case Left(why)   => MediaVerdict(rec.key, Some(item), rec.rights.toOption.map(_.licence.id), Some(why))
              case Right(full) =>
                val completed = PluginMedia.record(full)
                val again = PluginMedia
                  .refusal(completed, allow, name)
                  .map(why => if why.startsWith("licence") then "licence not allowed on the full record" else why)
                MediaVerdict(completed.key, Some(full), completed.rights.toOption.map(_.licence.id), again)

  /** One import of the plugin's event catalogue `catalogue`: its parts for `query`, each read page by page through the
    * catalogue's polite client, each record checked and dated as the server stores it (a part is read once per run; the
    * server also skips parts an earlier import read).
    */
  def readCatalogue(catalogue: String, query: CatalogueQuery): CatalogueReport =
    registries.eventCatalogues.get(catalogue) match
      case None =>
        CatalogueReport(Nil, Nil, Nil, Nil, Some(s"the plugin registered no event catalogue '$catalogue'"), Nil, Nil)
      case Some(cat) =>
        val pages = List.newBuilder[(String, Either[String, Int])]
        val accepted = List.newBuilder[AcceptedEvent]
        val refused = List.newBuilder[String]
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
                        case Left(why) => refused += s"$part: $why"
                        case Right(r)  =>
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
        CatalogueReport(parts, pages.result(), accepted.result(), refused.result(), failure, logged, asked)

  /** One model call to the plugin's AI provider `provider`, as the server's agents make it: the provider's check of
    * `request.model` first, then the call. `http` is what the provider sends through — [[ScriptedAiHttp]] in a test,
    * [[PluginHarness.liveAiHttp]] (the server's own client) to reach the real provider. (The server also refuses a
    * provider that spends the person's own plan while it listens beyond loopback; a test is on loopback.)
    */
  def aiTurn(provider: String, request: AiTurnRequest, http: AiHttp = ScriptedAiHttp()): AiReport =
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
  def resolveSecret(vault: String, ref: String): Either[String, String] =
    PluginHost.resolve(SecretRef.Vault(vault, ref), env, registries.secretVaults)

object PluginHarness:

  /** The id of the stand-in vault plugin that serves a test's `secrets` (never one of the plugin's). */
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
      id: String,
      config: String | Path = "",
      http: HttpMode = HttpMode.Offline,
      secrets: Map[String, String] = Map.empty,
      env: Map[String, String] = sys.env,
      histmersionConf: Option[Path] = None
  ): Either[String, PluginHarness] =
    val loader = Option(Thread.currentThread.getContextClassLoader).getOrElse(getClass.getClassLoader)
    val envSecrets = secrets.collect { case (k, v) if k.startsWith(s"${SecretRef.EnvScheme}:") => k.drop(4).trim -> v }
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

  private def configText(config: String | Path): Either[String, String] = config match
    case p: Path =>
      if Files.isRegularFile(p) then Right(Files.readString(p, UTF_8)) else Left(s"no config file $p")
    case s: String => Right(s)

  /** The plugin with this id among the providers ServiceLoader finds, or what is missing. */
  private def discover(loader: ClassLoader, id: String): Either[String, Plugin] =
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
  private def defaultsOf(p: Plugin): Either[String, String] =
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
  private def testVaults(secrets: Map[String, String]): Either[String, Option[Plugin]] =
    val refs = secrets.toList
      .filterNot(_._1.startsWith(s"${SecretRef.EnvScheme}:"))
      .map((k, v) => SecretRef.parse(k).map(_ -> v))
    refs.collectFirst { case Left(e) => e } match
      case Some(e) => Left(s"secrets: $e")
      case None    =>
        val byVault = refs
          .collect { case Right((SecretRef.Vault(vault, ref), v)) => (vault, ref, v) }
          .groupBy(_._1)
          .view
          .mapValues(_.map(t => t._2 -> t._3).toMap)
          .toMap
        Right(Option.when(byVault.nonEmpty)(TestVaults(TestVaultsId, byVault)))

  private def userConfig(id: String, text: String, withVaults: Boolean): Either[String, Config] =
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
    * e-mail left out so that no request of a test carries it.
    */
  private def hostConfig(env: Config, file: Option[Path]): Either[String, AppConfig] =
    val loaded = file match
      case None    => scala.util.Try(AppConfig.load(env)).toEither.left.map(_.getMessage)
      case Some(p) => AppConfig.loadWithUserFile(env, Some(p)).map(_.config)
    loaded.map(c => c.modify(_.acquire.contactEmail).setTo(""))

  /** The values of the `env:` and `value:` secret references a config text names. */
  private def referencedValues(text: String, env: Map[String, String]): Set[String] =
    val named = "env:([A-Za-z_][A-Za-z0-9_]*)".r.findAllMatchIn(text).flatMap(m => env.get(m.group(1))).toSet
    val literal = "value:([^\"\\s,}\\]]+)".r.findAllMatchIn(text).map(_.group(1)).toSet
    named ++ literal

  private def register(
      id: String,
      plugin: Plugin,
      vaults: Option[Plugin],
      user: Config,
      env: Config,
      host: AppConfig,
      mode: HttpMode,
      scrub: Scrubber
  ): Either[String, PluginHarness] =
    val lines = Lines()
    val wire = Wire(mode, scrub)
    val sleep: Long => Unit = if mode.live then ms => Thread.sleep(ms) else _ => ()
    val acquisition =
      PolicyHttp.acquisition(host, wire.transport(PolicyHttp.acquisitionTransport(host)), sleep, lines.host)
    val services = HostServices(() => acquisition.plugin, env, lines.plugin)
    val generation = PluginHost.build(plugin :: vaults.toList, user, env, services)
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
  private def save(dir: Path, fetcher: String, work: WorkKey, file: FetchedFile): Path =
    val ident =
      List(work.doi.map(_.value), work.isbn, work.arxiv, work.pmcid, work.pmid, work.archive).flatten.headOption
        .getOrElse("work")
    val ext = Option(file.extension).map(_.replaceAll("[^A-Za-z0-9]", "")).filter(_.nonEmpty).getOrElse("bin")
    Files.createDirectories(dir)
    val out = dir.resolve(s"$fetcher-${ident.replaceAll("[^A-Za-z0-9._-]", "_")}.$ext")
    Files.write(out, file.bytes)
