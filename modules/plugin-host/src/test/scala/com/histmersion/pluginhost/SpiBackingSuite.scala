package com.histmersion.pluginhost

import com.histmersion.plugin.*
import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.{Config, ConfigFactory}

import com.histmersion.plugin.PluginConfig.given

/** How the host backs the SPI members added within SPI 2: an answer with its status and headers sent once, the budget
  * of a metered upstream, the plugins a fetch is told of, the settings it supplies a first-party plugin from its own
  * keys (never in the report), first-party status for a reserved name, and a plugin's resolved secret by its setting.
  */
class SpiBackingSuite extends munit.FunSuite:

  private val noLog: (String, Boolean, String) => Unit = (_, _, _) => ()

  private def services(quota: String => UpstreamQuota = _ => UpstreamQuota.Unlimited): HostServices =
    HostServices(() => new PluginHttp { def get(u: String, h: Map[String, String]) = Left("offline") }, ConfigFactory.empty, noLog, quota)

  private def build(
      plugins: List[Plugin],
      user: String,
      env: Map[String, String] = Map.empty,
      fallback: String => Config = _ => ConfigFactory.empty,
      firstParty: String => Boolean = _ => false,
      quota: String => UpstreamQuota = _ => UpstreamQuota.Unlimited
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

  // ── SPI-1: PluginHttp.fetch ─────────────────────────────────────────────────────────────────

  test("the acquisition client's fetch is sent once, with the policy's User-Agent, and answers as it came"):
    val sent = scala.collection.mutable.ListBuffer.empty[(String, Map[String, String])]
    val wire = new HttpTransport:
      def get(url: String, headers: Map[String, String]) =
        sent += url -> headers
        Right(HttpAnswer(429, Map("x-ratelimit-remaining" -> "0"), "slow down".getBytes("UTF-8")))
    val policy = PolicyHttp("histmersion/0.1", wire, sleep = _ => fail("a fetch never waits"))
    val r = policy.plugin.fetch("https://core.example/v3/search", Map("Authorization" -> "Bearer k")).fold(fail(_), identity)
    assertEquals((r.status, r.header("X-RateLimit-Remaining"), String(r.body, "UTF-8")), (429, Some("0"), "slow down"))
    assertEquals(sent.toList, List("https://core.example/v3/search" -> Map("Authorization" -> "Bearer k", "User-Agent" -> "histmersion/0.1")))
    // `get` keeps asking a slow-down again, as before
    sent.clear()
    val retried = PolicyHttp("histmersion/0.1", wire, tries = 2, sleep = _ => ()).plugin.get("https://core.example/x")
    assert(retried.isLeft && sent.size == 2, sent.toString)

  // ── SPI-2: PluginContext.quota ──────────────────────────────────────────────────────────────

  final private case class NoSettings() derives ConfigReader

  /** A plugin that keeps the context's quota for `upstream`, as a fetcher does at registration. */
  final private class Metered(upstream: String) extends Plugin:
    type Config = NoSettings
    val info = PluginInfo("zq-metered", "1.0.0", Spi.Version)
    // the registration's quota, kept for the plugin's fetches
    var quota: Option[UpstreamQuota] = None
    def register(c: NoSettings, ctx: PluginContext) =
      quota = Some(ctx.quota(upstream))
      Right(())

  test("a plugin's quota for an upstream is the host's own budget for it; any other upstream is unlimited"):
    val taken = scala.collection.mutable.ListBuffer.empty[String]
    val core = new UpstreamQuota:
      def take(purpose: String) = { taken += purpose; if taken.size > 1 then Left("spent") else Right(()) }
      def observe(status: Int, headers: Map[String, String]) = ()
    val p = Metered("core")
    val g = build(List(p), "histmersion.plugins.zq-metered.enabled = true", quota = n => if n == "core" then core else UpstreamQuota.Unlimited)
    assert(g.report.ok, g.report.toString)
    assertEquals(p.quota.map(_.take("lookup")), Some(Right(())))
    assertEquals(p.quota.map(_.take("lookup")), Some(Left("spent")))
    assertEquals(taken.toList, List("lookup", "lookup"))
    val other = Metered("elsewhere")
    build(List(other), "histmersion.plugins.zq-metered.enabled = true", quota = n => if n == "core" then core else UpstreamQuota.Unlimited)
    assertEquals(other.quota, Some(UpstreamQuota.Unlimited))

  // ── SPI-3: FetchContext.registered ──────────────────────────────────────────────────────────

  test("a fetch is told which plugins its generation registered"):
    val asked = scala.collection.mutable.ListBuffer.empty[(String, Boolean)]
    val peerAware = new SourceFetcher:
      val access = Access.Open
      def fetch(work: WorkKey, ctx: FetchContext) =
        List("oa-unpaywall", "oa-openalex").foreach(id => asked += id -> ctx.registered(id))
        Fetch.Miss("nothing")
    val (chain, _) = FetchChain.of(scala.collection.immutable.ListMap("peer" -> peerAware), Nil, false, plugins = Set("oa-unpaywall"))
    chain.fetchWorkExplained(WorkKey(archive = Some("x")), legalOnly = true)
    assertEquals(asked.toList, List("oa-unpaywall" -> true, "oa-openalex" -> false))
    asked.clear()
    FetchChain(List("peer" -> peerAware)).fetchWorkExplained(WorkKey(archive = Some("x")), legalOnly = true)
    assertEquals(asked.toList.map(_._2).distinct, List(false))

  test("a generation names its registered plugins, a disabled or failed one not among them"):
    val g = build(
      List(Metered("core")),
      "histmersion.plugins.zq-metered.enabled = true"
    )
    assertEquals(g.registered, Set("zq-metered"))
    assertEquals(build(List(Metered("core")), "histmersion.plugins.zq-metered.enabled = false").registered, Set.empty)

  // ── host-supplied settings ──────────────────────────────────────────────────────────────────

  final private case class Endpoint(url: String) derives ConfigReader
  final private case class Host(endpoint: Endpoint) derives ConfigReader
  final private case class Supplied(contactEmail: Option[String], acquire: Host) derives ConfigReader

  /** A first-party plugin that reads a host key (`acquire.endpoint.url`) and has a setting of its own. */
  final private class FirstParty extends Plugin:
    type Config = Supplied
    val info = PluginInfo("zq-first", "1.0.0", Spi.Version)
    // what the plugin was given at registration
    var received: Option[Supplied] = None
    override def defaultsResource = "zq-first-defaults.conf"
    def register(c: Supplied, ctx: PluginContext) = { received = Some(c); Right(()) }

  private val supplied: String => Config = id =>
    if id == "zq-first" then
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
    assert(g.report.plugins.head.error.exists(_.contains("unknown setting")), g.report.toString)

  // ── first-party status ──────────────────────────────────────────────────────────────────────

  final private class Volcanoes(id: String) extends Plugin:
    type Config = NoSettings
    val info = PluginInfo(id, "1.0.0", Spi.Version)
    def register(c: NoSettings, ctx: PluginContext) = ctx.eventCatalogues.add("gvp", Catalogue)

  private object Catalogue extends EventCatalogue:
    def title = "Volcanoes"
    def licence = Licence("CC0-1.0")
    def citation = "Volcanoes"
    def home = "https://volcanoes.example"
    def parts(query: CatalogueQuery) = Nil
    def read(part: String, query: CatalogueQuery, http: PluginHttp) = Right(CataloguePage(Nil))

  test("a reserved catalogue name is kept for the first-party plugin; any other plugin is refused it"):
    val third = build(List(Volcanoes("zq-volcanoes")), "histmersion.plugins.zq-volcanoes.enabled = true")
    assertEquals(third.report.plugins.head.state, "failed")
    assert(third.report.plugins.head.error.exists(_.contains("Histmersion's own event catalogues")), third.report.toString)
    val first = build(
      List(Volcanoes("events-gvp")),
      "histmersion.plugins.events-gvp.enabled = true",
      firstParty = ReservedNames.firstPartyPlugins
    )
    assert(first.report.ok, first.report.toString)
    assertEquals(first.registries.eventCatalogues.keySet, Set("gvp"))

  // ── a plugin's resolved secret ──────────────────────────────────────────────────────────────

  final private case class Keyed(apiKey: Option[Secret]) derives ConfigReader

  final private class KeyedPlugin extends Plugin:
    type Config = Keyed
    val info = PluginInfo("zq-keyed", "1.0.0", Spi.Version)
    override def defaultsResource = "zq-keyed-defaults.conf"
    def register(c: Keyed, ctx: PluginContext) = c.apiKey.fold(Right(()))(ctx.secret(_).map(_ => ()))

  test("a generation gives the host a plugin's secret by its setting, as it resolved it — never another's"):
    val g = build(
      List(KeyedPlugin()),
      """histmersion.plugins.zq-keyed { enabled = true, api-key = "env:ZQ_KEY" }""",
      Map("ZQ_KEY" -> "k-secret")
    )
    assert(g.report.ok, g.report.toString)
    assertEquals(g.secret("zq-keyed", "api-key"), Some("k-secret"))
    assertEquals(g.secret("zq-keyed", "enabled"), None)
    assertEquals(g.secret("zq-other", "api-key"), None)
    val none = build(List(KeyedPlugin()), "histmersion.plugins.zq-keyed.enabled = true")
    assertEquals(none.secret("zq-keyed", "api-key"), None)
