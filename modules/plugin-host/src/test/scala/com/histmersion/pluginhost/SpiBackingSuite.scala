package com.histmersion
package pluginhost

import com.histmersion.util.problem.{External, Problem, Problems}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.*
import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.{Config, ConfigFactory}

import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.domain.CoreQuota
import com.histmersion.domain.HttpPolicy

/** How the host backs the SPI members added within SPI 2: an answer with its status and headers sent once, the budget
  * of a metered upstream, the plugins a fetch is told of, the settings it supplies a first-party plugin from its own
  * keys (never in the report), first-party status for a reserved name, and a plugin's resolved secret by its setting.
  */
class SpiBackingSuite extends munit.FunSuite:

  private def services(quota: HostServices.Upstream => UpstreamQuota = _ => UpstreamQuota.Unlimited): HostServices =
    HostServices(
      () =>
        new Plugin.Http {
          def get(u: Plugin.Http.Url, h: Map[Header.Name, Header.Value]) =
            Left(
              Problem.Upstream.Unreachable.here(
                Problem.Upstream.Name("x"),
                Problem.Upstream.Url("https://x.example"),
                External.Reported("offline")
              )
            )
        },
      ConfigFactory.empty,
      (_, _, _) => (),
      quota
    )

  private def build(
      plugins: List[Plugin],
      user: String,
      env: Map[String, String] = Map.empty,
      fallback: Plugin.Info.Id => Config = _ => ConfigFactory.empty,
      firstParty: Plugin.Info.Id => Boolean = _ => false,
      quota: HostServices.Upstream => UpstreamQuota = _ => UpstreamQuota.Unlimited
  ): Generation =
    import scala.jdk.CollectionConverters.*
    PluginHost.build(
      plugins,
      ConfigFactory.parseString(user),
      ConfigFactory.parseMap(env.asJava),
      services(quota),
      fallback = fallback,
      firstParty = firstParty
    )

  // ── SPI-1: Plugin.Http.fetch ─────────────────────────────────────────────────────────────────

  test("the acquisition client's fetch is sent once, with the policy's User-Agent, and answers as it came"):
    val sent = scala.collection.mutable.ListBuffer.empty[(String, Map[String, String])]
    val wire = new HttpTransport:
      def get(url: HttpTransport.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]) =
        sent += url.value -> headers.map((k, v) => k.value -> v.value)
        Right(
          HttpAnswer(
            HttpAnswer.Code(429),
            Map(CoreQuota.HeaderName("x-ratelimit-remaining") -> CoreQuota.HeaderValue("0")),
            HttpAnswer.Body("slow down".getBytes("UTF-8"))
          )
        )
    val policy = PolicyHttp(HttpPolicy.UserAgent("histmersion/0.1"), wire, sleep = _ => fail("a fetch never waits"))
    val r =
      policy.plugin
        .fetch(
          Plugin.Http.Url("https://core.example/v3/search"),
          Map(Header.Name("Authorization") -> Header.Value("Bearer k"))
        )
        .fold(e => fail(e.sentence), identity)
    assertEquals(
      (r.status, r.header(Header.Name("X-RateLimit-Remaining")), String(r.body.value, "UTF-8")),
      (429, Some("0"), "slow down")
    )
    assertEquals(
      sent.toList,
      List("https://core.example/v3/search" -> Map("Authorization" -> "Bearer k", "User-Agent" -> "histmersion/0.1"))
    )
    // `get` keeps asking a slow-down again, as before
    sent.clear()
    val retried =
      PolicyHttp(HttpPolicy.UserAgent("histmersion/0.1"), wire, tries = PolicyHttp.Tries(2), sleep = _ => ()).plugin
        .get(Plugin.Http.Url("https://core.example/x"))
    assert(retried.isLeft && sent.size == 2, sent.toString)

  // ── SPI-2: Plugin.Context.quota ──────────────────────────────────────────────────────────────

  final private case class NoSettings() derives ConfigReader

  /** A plugin that keeps the context's quota for `upstream`, as a fetcher does at registration. */
  final private class Metered(upstream: Plugin.Context.QuotaUpstream) extends Plugin:
    type Config = NoSettings
    val info =
      Plugin.Info(Plugin.Info.Id("zq-metered"), Plugin.Info.Version("1.0.0"), Plugin.Info.SpiVersion(Spi.Version))
    // the registration's quota, kept for the plugin's fetches
    var quota: Option[UpstreamQuota] = None
    def register(c: NoSettings, ctx: Plugin.Context) =
      quota = Some(ctx.quota(upstream))
      Right(())

  test("a plugin's quota for an upstream is the host's own budget for it; any other upstream is unlimited"):
    val taken = scala.collection.mutable.ListBuffer.empty[String]
    val core = new UpstreamQuota:
      def take(purpose: UpstreamQuota.TakePurpose) = {
        taken += purpose.value
        if taken.size > 1 then Left(Problem.Upstream.Limited(Problem.Upstream.Name("core"), None))
        else Right(())
      }
      def observe(
          status: UpstreamQuota.ObserveStatus,
          headers: Map[Header.Name, Header.Value]
      ) = ()
    val p = Metered(Plugin.Context.QuotaUpstream("core"))
    val g = build(
      List(p),
      "histmersion.plugins.zq-metered.enabled = true",
      quota = n => if n.value == "core" then core else UpstreamQuota.Unlimited
    )
    assert(g.report.ok, g.report.toString)
    assertEquals(p.quota.map(_.take(UpstreamQuota.TakePurpose("lookup"))), Some(Right(())))
    assertEquals(
      p.quota.map(_.take(UpstreamQuota.TakePurpose("lookup")).left.map(_.sentence)),
      Some(Left("core's usage limit is reached"))
    )
    assertEquals(taken.toList, List("lookup", "lookup"))
    val other = Metered(Plugin.Context.QuotaUpstream("elsewhere"))
    build(
      List(other),
      "histmersion.plugins.zq-metered.enabled = true",
      quota = n => if n.value == "core" then core else UpstreamQuota.Unlimited
    )
    assertEquals(other.quota, Some(UpstreamQuota.Unlimited))

  // ── SPI-3: Fetch.Context.registered ──────────────────────────────────────────────────────────

  test("a fetch is told which plugins its generation registered"):
    val asked = scala.collection.mutable.ListBuffer.empty[(String, Boolean)]
    val peerAware = new SourceFetcher:
      val access = Access.Open
      def fetch(work: WorkKey, ctx: Fetch.Context) =
        List("oa-unpaywall", "oa-openalex").foreach(id =>
          asked += id -> ctx.registered(Fetch.Context.RegisteredPluginId(id))
        )
        Fetch.Miss(Fetch.Absence.NotHeld, Problem.NotFound.Copy(Problem.Raw("nothing"), Vector.empty))
    val (chain, _) =
      FetchChain.of(
        scala.collection.immutable.ListMap(util.plugins.Plugin.Id("peer") -> peerAware),
        Nil,
        FetchChain.AllowUnauthorised(false),
        plugins = Set(util.plugins.Plugin.Id("oa-unpaywall"))
      )
    chain.fetchWorkExplained(WorkKey(archive = Some(WorkKey.Archive("x"))), legalOnly = FetchChain.LegalOnly(true))
    assertEquals(asked.toList, List("oa-unpaywall" -> true, "oa-openalex" -> false))
    asked.clear()
    FetchChain(List(FetchChain.Step.Id("peer") -> peerAware))
      .fetchWorkExplained(WorkKey(archive = Some(WorkKey.Archive("x"))), legalOnly = FetchChain.LegalOnly(true))
    assertEquals(asked.toList.map(_._2).distinct, List(false))

  test("a generation names its registered plugins, a disabled or failed one not among them"):
    val g = build(
      List(Metered(Plugin.Context.QuotaUpstream("core"))),
      "histmersion.plugins.zq-metered.enabled = true"
    )
    assertEquals(g.registered, Set("zq-metered"))
    assertEquals(
      build(
        List(Metered(Plugin.Context.QuotaUpstream("core"))),
        "histmersion.plugins.zq-metered.enabled = false"
      ).registered,
      Set.empty
    )

  // ── host-supplied settings ──────────────────────────────────────────────────────────────────

  final private case class Endpoint(url: String) derives ConfigReader
  final private case class Host(endpoint: Endpoint) derives ConfigReader
  final private case class Supplied(contactEmail: Option[String], acquire: Host) derives ConfigReader

  /** A first-party plugin that reads a host key (`acquire.endpoint.url`) and has a setting of its own. */
  final private class FirstParty extends Plugin:
    type Config = Supplied
    val info =
      Plugin.Info(Plugin.Info.Id("zq-first"), Plugin.Info.Version("1.0.0"), Plugin.Info.SpiVersion(Spi.Version))
    // what the plugin was given at registration
    var received: Option[Supplied] = None
    override def defaultsResource = Plugin.DefaultsResource("zq-first-defaults.conf")
    def register(c: Supplied, ctx: Plugin.Context) = { received = Some(c); Right(()) }

  private val supplied: Plugin.Info.Id => Config = id =>
    if id.value == "zq-first" then
      ConfigFactory.parseString("""acquire.endpoint.url = "https://host.example", contact-email = "host@example.org"""")
    else ConfigFactory.empty

  test("the host supplies a first-party plugin's host keys: under the user's block, over the defaults, never reported"):
    val p = FirstParty()
    val g = build(List(p), "histmersion.plugins.zq-first.enabled = true", fallback = supplied)
    assert(g.report.ok, g.report.toString)
    assertEquals(p.received, Some(Supplied(Some("host@example.org"), Host(Endpoint("https://host.example")))))
    // the report lists the plugin's own block alone: its defaults, not the host's keys
    assertEquals(g.report.plugins.head.settings.map(s => s.key -> s.value), List("contact-email" -> "null"))
    // the user's own block wins over the host's keys
    val mine = FirstParty()
    build(
      List(mine),
      """histmersion.plugins.zq-first { enabled = true, contact-email = "mine@example.org" }""",
      fallback = supplied
    )
    assertEquals(mine.received.flatMap(_.contactEmail), Some("mine@example.org"))
    // … and so does the environment override its defaults declare
    val env = FirstParty()
    build(
      List(env),
      "histmersion.plugins.zq-first.enabled = true",
      Map("ZQ_FIRST_CONTACT" -> "env@example.org"),
      fallback = supplied
    )
    assertEquals(env.received.flatMap(_.contactEmail), Some("env@example.org"))

  test("a user cannot write a host key into a first-party plugin's block: it is not one of its settings"):
    val g = build(
      List(FirstParty()),
      """histmersion.plugins.zq-first { enabled = true, acquire.endpoint.url = "https://elsewhere.example" }""",
      fallback = supplied
    )
    assertEquals(g.report.plugins.head.state, "failed")
    assert(
      g.report.plugins.head.errors.exists {
        case Problem.Config.Setting(_, Problem.Config.Fault.NotInDefaults) => true
        case _                                                             => false
      },
      g.report.toString
    )

  // ── first-party status ──────────────────────────────────────────────────────────────────────

  final private class Volcanoes(id: Plugin.Info.Id) extends Plugin:
    type Config = NoSettings
    val info = Plugin.Info(id, Plugin.Info.Version("1.0.0"), Plugin.Info.SpiVersion(Spi.Version))
    def register(c: NoSettings, ctx: Plugin.Context) =
      ctx.eventCatalogues.add(Registry.AddId("gvp"), Catalogue)

  private object Catalogue extends EventCatalogue:
    def title = EventCatalogue.Title("Volcanoes")
    def licence = Licence("CC0-1.0")
    def citation = EventCatalogue.Citation("Volcanoes")
    def home = EventCatalogue.Home("https://volcanoes.example")
    def parts(query: CatalogueQuery) = Nil
    def read(part: EventCatalogue.ReadPart, query: CatalogueQuery, http: Plugin.Http) = Right(CataloguePage(Nil))

  test("a reserved catalogue name is kept for the first-party plugin; any other plugin is refused it"):
    val third =
      build(List(Volcanoes(Plugin.Info.Id("zq-volcanoes"))), "histmersion.plugins.zq-volcanoes.enabled = true")
    assertEquals(third.report.plugins.head.state, "failed")
    assertEquals(
      third.report.plugins.head.errors,
      List(Problem.Conflict.AlreadyExists(Problem.TypeName.named[EventCatalogue], Problem.Raw("gvp")))
    )
    val first = build(
      List(Volcanoes(Plugin.Info.Id("events-gvp"))),
      "histmersion.plugins.events-gvp.enabled = true",
      firstParty = id => ReservedNames.firstPartyPlugins.contains(ReservedNames.Name(id.value))
    )
    assert(first.report.ok, first.report.toString)
    assertEquals(first.registries.eventCatalogues.keySet, Set("gvp"))

  // ── a plugin's resolved secret ──────────────────────────────────────────────────────────────

  final private case class Keyed(apiKey: Option[Secret]) derives ConfigReader

  final private class KeyedPlugin extends Plugin:
    type Config = Keyed
    val info =
      Plugin.Info(Plugin.Info.Id("zq-keyed"), Plugin.Info.Version("1.0.0"), Plugin.Info.SpiVersion(Spi.Version))
    override def defaultsResource = Plugin.DefaultsResource("zq-keyed-defaults.conf")
    def register(c: Keyed, ctx: Plugin.Context) =
      c.apiKey.fold[Either[Problems, Unit]](Right(()))(s => ctx.secret(s).map(_ => ()))

  test("a generation gives the host a plugin's secret by its setting, as it resolved it — never another's"):
    val g = build(
      List(KeyedPlugin()),
      """histmersion.plugins.zq-keyed { enabled = true, api-key = "env:ZQ_KEY" }""",
      Map("ZQ_KEY" -> "k-secret")
    )
    assert(g.report.ok, g.report.toString)
    assertEquals(g.secret(Plugin.Info.Id("zq-keyed"), PluginHost.ConfigPath("api-key")), Some("k-secret"))
    assertEquals(g.secret(Plugin.Info.Id("zq-keyed"), PluginHost.ConfigPath("enabled")), None)
    assertEquals(g.secret(Plugin.Info.Id("zq-other"), PluginHost.ConfigPath("api-key")), None)
    val none = build(List(KeyedPlugin()), "histmersion.plugins.zq-keyed.enabled = true")
    assertEquals(none.secret(Plugin.Info.Id("zq-keyed"), PluginHost.ConfigPath("api-key")), None)
