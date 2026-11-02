package com.histmersion.plugin

/** The SPI members added within SPI 2 for the first-party plugins — an answer's status and headers, an upstream's
  * budget, the peers of a fetch, a catalogue's re-reading and its changing parts, an AI provider's request options —
  * and their defaults, which keep a plugin or a host written before them compiling and linking.
  */
class SpiAdditionsSuite extends munit.FunSuite:

  private object Bodies extends Plugin.Http:
    def get(url: String, headers: Map[String, String]) =
      if url.endsWith("/missing") then Left(s"$url: HTTP 404") else Right(s"body of $url".getBytes("UTF-8"))

  test("Plugin.Http.fetch from a host that has only bodies: status 200, no headers, or the same Left"):
    val r = Bodies.fetch("https://x.example/a").fold(e => fail(e), identity)
    assertEquals(
      (r.status, r.headers, String(r.body, "UTF-8")),
      (200, Map.empty[String, String], "body of https://x.example/a")
    )
    assertEquals(Bodies.fetch("https://x.example/missing").map(_.status), Left("https://x.example/missing: HTTP 404"))

  test("a response's header is found in any case"):
    val r = Plugin.HttpResponse(429, Map("x-ratelimit-remaining" -> "0"), Array.emptyByteArray)
    assertEquals(r.header("X-RateLimit-Remaining"), Some("0"))
    assertEquals(r.header("Retry-After"), None)

  test("the unlimited quota lets every call go and keeps nothing"):
    val q = UpstreamQuota.Unlimited
    assertEquals(List.fill(1000)(q.take("lookup")).distinct, List(Right(())))
    q.observe(429, Map("retry-after" -> "3600"))
    assertEquals(q.take("search"), Right(()))
    assertEquals(q.waitingUntil, None)

  /** A context as a plugin of SPI 2's first release wrote it: no quota of its own. */
  private object EarlyContext extends Plugin.Context:
    def http = Bodies
    def log = new Plugin.Log { def info(m: String) = (); def warn(m: String) = () }
    def env(name: String) = None
    def secret(s: Secret) = Left("none")
    def secretVaults = Registry.refusing("no vaults")

  test("a context written before budgets existed lends the unlimited quota for any upstream"):
    assertEquals(EarlyContext.quota("core"), UpstreamQuota.Unlimited)
    assertEquals(EarlyContext.quota("anything").take("lookup"), Right(()))

  test("a fetch context written before peers were told says no plugin is registered"):
    val ctx = new Fetch.Context:
      def memo[A](key: String)(compute: => A): A = compute
      val log = new Plugin.Log { def info(m: String) = (); def warn(m: String) = () }
    assert(!ctx.registered("oa-unpaywall"))

  /** A catalogue as one written before re-reading existed: it re-reads nothing, and its parts stay read. */
  private object Floods extends EventCatalogue:
    def title = "Floods"
    def licence = Licence("CC0-1.0")
    def citation = "Floods"
    def home = "https://floods.example"
    def parts(query: CatalogueQuery) = List("all")
    def read(part: String, query: CatalogueQuery, http: Plugin.Http) = Right(CataloguePage(Nil))

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
      def turn(request: AiTurn.Request, http: AiHttp) = Left(AiFailure.Error("none"))
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
    val old = AiTurn.Request("m", "s", Nil, Nil, Some("{}"), 10, 1, 2)
    assertEquals((old.answerSchemaRendered, old.answerDescription), (None, None))
    assertEquals(new AiTurn.Request("m", "s", Nil, Nil, Some("{}"), 10, 1, 2), old)
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
    assert(classOf[AiTurn.Request].getConstructor(request*) != null)
    assert(AiTurn.Request.getClass.getMethod("apply", request*) != null)

  // ── media: the members added for the first-party collections ─────────────────────────────────

  /** A collection as a plugin of SPI 2's first release wrote it: a title, a role and a search. */
  private object Portal extends MediaCollection:
    val title = "Portal objects"
    val role = MediaCollection.Object
    def search(place: MediaPlace, http: Plugin.Http) =
      if place.label == "Nowhere" then Left("offline")
      else
        Right(
          List(
            Right(MediaItem(s"${place.id}-1", place.label, "https://portal.example/1", None, Left("closed"))),
            Left("one record unreadable")
          )
        )

  test("an item built as SPI 2's first release built it has no role of its own and no rendition"):
    val rights = Left("not open access")
    val applied = MediaItem("k", "t", "https://p", None, rights, None, None, Nil, None, Nil, None)
    val constructed = new MediaItem("k", "t", "https://p", None, rights, None, None, Nil, None, Nil, Some("withheld"))
    assertEquals((applied.role, applied.rendition), (None, None))
    assertEquals((constructed.role, constructed.rendition, constructed.withheld), (None, None, Some("withheld")))
    assertEquals(applied, MediaItem("k", "t", "https://p", None, rights))

  test("a place described as SPI 2's first release described it carries no gazetteer ids"):
    val applied = MediaPlace("p", "Mycenae", None, None, None, None)
    val constructed = new MediaPlace("p", "Mycenae", None, None, None, None)
    assertEquals((applied.ids, constructed.ids), (Nil, Nil))
    assertEquals(applied, MediaPlace("p", "Mycenae"))

  test("a collection's new members by default: its role alone, places, no events, always asked, refusals final"):
    assertEquals(Portal.roles, Set(MediaCollection.Object))
    assertEquals(Portal.asks, Set(MediaTarget.Places))
    assertEquals(Portal.searchEvent(MediaEvent("e", "Fall of X"), Bodies), Right(Nil))
    assertEquals(Portal.check, Right(()))
    assertEquals(Portal.refusalBackoffMs, Nil)
    assertEquals(Portal.latestYear, None)
    assertEquals(Portal.placesCarrying, None)

  test("searchTargets by default: search for each place and searchEvent for each event, in order, failures counted"):
    val found = Portal.searchTargets(
      List(MediaPlace("a", "Mycenae"), MediaPlace("b", "Nowhere")),
      List(MediaEvent("e", "Fall of X")),
      Bodies
    )
    val (a, b, e) =
      (
        MediaTarget.Id(MediaTarget.Places, "a"),
        MediaTarget.Id(MediaTarget.Places, "b"),
        MediaTarget.Id(MediaTarget.Events, "e")
      )
    assertEquals(found.uses.map(u => (u.target, u.item.key, u.rank)), List((a, "a-1", 0)))
    assertEquals(found.asked, List(a, b, e))
    assertEquals(found.unanswered, Set(b), "a failed search is not recorded as asked")
    assertEquals(found.read, 2)
    assertEquals(found.failed, List("a: one record unreadable", "Portal objects search for b: offline"))
    assertEquals((found.notTaken, found.categories), (Nil, Nil))

  test("completeAll by default: complete for each item, by its key"):
    val items =
      List(MediaItem("k1", "t", "https://p", None, Left("x")), MediaItem("k2", "t", "https://p", None, Left("y")))
    assertEquals(Portal.completeAll(items, Bodies), MediaCompleted(items.map(i => i.key -> i).toMap))
    val failing = new MediaCollection:
      val title = "Failing"
      val role = MediaCollection.Map
      def search(place: MediaPlace, http: Plugin.Http) = Right(Nil)
      override def complete(item: MediaItem, http: Plugin.Http) =
        if item.key == "k1" then Left("gone") else Right(item)
    assertEquals(failing.completeAll(items, Bodies), MediaCompleted(Map("k2" -> items(1)), List("k1: gone")))

  test("the roles a collection's pictures may play are the ones the host stores"):
    assertEquals(
      MediaCollection.Roles,
      Set("object", "map", "image", "locator-map", "plan", "aerial-view", "route-map", "depiction")
    )

  test("a context written before the host lent its licence reading reads every statement as unknown"):
    val l = EarlyContext.licences
    assertEquals(l.readName("CC BY 4.0"), Licence.Read("CC BY 4.0", Licence.Read.Unknown))
    assertEquals(
      l.readUrl("https://creativecommons.org/licenses/by/4.0"),
      Licence.Read.unknown("https://creativecommons.org/licenses/by/4.0")
    )
    assertEquals(l.readFirst(List(" ", "cc0", "pd")), Licence.Read("cc0", Licence.Read.Unknown))
    assertEquals(l.readFirst(Nil), Licence.Read.unknown(""))
    assert(!l.readName("cc-by-4.0").needsAttribution)
