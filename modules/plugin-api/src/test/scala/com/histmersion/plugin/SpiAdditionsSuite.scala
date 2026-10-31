package com.histmersion.plugin

/** The SPI members added within SPI 2 for the first-party plugins — an answer's status and headers, an upstream's
  * budget, the peers of a fetch, a catalogue's re-reading and its changing parts, an AI provider's request options —
  * and their defaults, which keep a plugin or a host written before them compiling and linking.
  */
class SpiAdditionsSuite extends munit.FunSuite:

  private object Bodies extends PluginHttp:
    def get(url: String, headers: Map[String, String]) =
      if url.endsWith("/missing") then Left(s"$url: HTTP 404") else Right(s"body of $url".getBytes("UTF-8"))

  test("PluginHttp.fetch from a host that has only bodies: status 200, no headers, or the same Left"):
    val r = Bodies.fetch("https://x.example/a").fold(e => fail(e), identity)
    assertEquals(
      (r.status, r.headers, String(r.body, "UTF-8")),
      (200, Map.empty[String, String], "body of https://x.example/a")
    )
    assertEquals(Bodies.fetch("https://x.example/missing").map(_.status), Left("https://x.example/missing: HTTP 404"))

  test("a response's header is found in any case"):
    val r = PluginHttpResponse(429, Map("x-ratelimit-remaining" -> "0"), Array.emptyByteArray)
    assertEquals(r.header("X-RateLimit-Remaining"), Some("0"))
    assertEquals(r.header("Retry-After"), None)

  test("the unlimited quota lets every call go and keeps nothing"):
    val q = UpstreamQuota.Unlimited
    assertEquals(List.fill(1000)(q.take("lookup")).distinct, List(Right(())))
    q.observe(429, Map("retry-after" -> "3600"))
    assertEquals(q.take("search"), Right(()))
    assertEquals(q.waitingUntil, None)

  /** A context as a plugin of SPI 2's first release wrote it: no quota of its own. */
  private object EarlyContext extends PluginContext:
    def http = Bodies
    def log = new PluginLog { def info(m: String) = (); def warn(m: String) = () }
    def env(name: String) = None
    def secret(s: Secret) = Left("none")
    def secretVaults = Registry.refusing("no vaults")

  test("a context written before budgets existed lends the unlimited quota for any upstream"):
    assertEquals(EarlyContext.quota("core"), UpstreamQuota.Unlimited)
    assertEquals(EarlyContext.quota("anything").take("lookup"), Right(()))

  test("a fetch context written before peers were told says no plugin is registered"):
    val ctx = new FetchContext:
      def memo[A](key: String)(compute: => A): A = compute
      val log = new PluginLog { def info(m: String) = (); def warn(m: String) = () }
    assert(!ctx.registered("oa-unpaywall"))

  /** A catalogue as one written before re-reading existed: it re-reads nothing, and its parts stay read. */
  private object Floods extends EventCatalogue:
    def title = "Floods"
    def licence = Licence("CC0-1.0")
    def citation = "Floods"
    def home = "https://floods.example"
    def parts(query: CatalogueQuery) = List("all")
    def read(part: String, query: CatalogueQuery, http: PluginHttp) = Right(CataloguePage(Nil))

  test("a catalogue re-reads nothing by default, and a part read once stays read"):
    val stored = CatalogueEvent("f1", "https://floods.example/f1", "Flood", "flood", Some(Year(-500)), Some(Year(-500)))
    assertEquals(Floods.reread(stored), None)
    assert(!Floods.everyPartEachImport)

  // ── the AI provider's request options (the HTTP built-ins') ──────────────────────────────────

  test("a provider asks for no request option by default: no iteration notes, its failures prefixed as before"):
    val caps = AiCapabilities()
    assertEquals((caps.iterationMarkers, caps.verbatimFailures), (false, false))
    val early = new AiProvider:
      def title = "Early"
      def turn(request: AiTurnRequest, http: AiHttp) = Left(AiFailure.Error("none"))
    assertEquals(early.capabilities, AiCapabilities(tools = true, structuredOutput = false))

  test("the members a provider or host compiled before them used still exist, with their old shapes"):
    val longs = Array.fill(4)(classOf[Long])
    assertEquals(new AiUsage(1, 2, 3, 4), AiUsage(1, 2, 3, 4, 0))
    assertEquals(AiUsage(1, 2, 3, 4).reasoning, 0L)
    assert(classOf[AiUsage].getConstructor(longs*) != null)
    assert(AiUsage.getClass.getMethod("apply", longs*) != null)
    val bools = Array.fill(2)(classOf[Boolean])
    assertEquals(new AiCapabilities(false, true), AiCapabilities(false, true, false, false))
    assert(classOf[AiCapabilities].getConstructor(bools*) != null)
    assert(AiCapabilities.getClass.getMethod("apply", bools*) != null)
    val old = AiTurnRequest("m", "s", Nil, Nil, Some("{}"), 10, 1, 2)
    assertEquals((old.answerSchemaRendered, old.answerDescription), (None, None))
    assertEquals(new AiTurnRequest("m", "s", Nil, Nil, Some("{}"), 10, 1, 2), old)
    val request = Array[Class[?]](
      classOf[String],
      classOf[String],
      classOf[List[?]],
      classOf[List[?]],
      classOf[Option[?]],
      classOf[Int],
      classOf[Int],
      classOf[Int]
    )
    assert(classOf[AiTurnRequest].getConstructor(request*) != null)
    assert(AiTurnRequest.getClass.getMethod("apply", request*) != null)
