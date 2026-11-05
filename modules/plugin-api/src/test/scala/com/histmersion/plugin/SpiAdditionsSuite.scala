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
    val r = Bodies.fetch(_root_.com.histmersion.plugin.Plugin.Http.GetUrl("https://x.example/a")).fold(e => fail((e).value), identity)
    assertEquals(
      (r.status, r.headers, String(r.body, "UTF-8")),
      (200, Map.empty[String, String], "body of https://x.example/a")
    )
    assertEquals((((Bodies.fetch(_root_.com.histmersion.plugin.Plugin.Http.GetUrl("https://x.example/missing")).map(_.status)).left.map(refactorError => (refactorError).value)).map(refactorValue => (refactorValue).value): Either[String, Int]), Left("https://x.example/missing: HTTP 404"))

  test("a response's header is found in any case"):
    val r = Plugin.HttpResponse(_root_.com.histmersion.plugin.Plugin.HttpResponse.Status(429), Map({ val refactorTuple = "x-ratelimit-remaining" -> "0"; (_root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersKey(refactorTuple._1), _root_.com.histmersion.plugin.Plugin.HttpResponse.HeadersValue(refactorTuple._2)) }), Array.emptyByteArray)
    assertEquals(r.header("X-RateLimit-Remaining"), Some("0"))
    assertEquals(r.header("Retry-After"), None)

  test("the unlimited quota lets every call go and keeps nothing"):
    val q = UpstreamQuota.Unlimited
    assertEquals(List.fill(1000)(q.take(_root_.com.histmersion.plugin.UpstreamQuota.TakePurpose("lookup"))).distinct, List(Right(())))
    q.observe(_root_.com.histmersion.plugin.UpstreamQuota.ObserveStatus(429), Map({ val refactorTuple2 = "retry-after" -> "3600"; (_root_.com.histmersion.plugin.UpstreamQuota.ObserveHeadersPart1(refactorTuple2._1), _root_.com.histmersion.plugin.UpstreamQuota.ObserveHeadersPart2(refactorTuple2._2)) }))
    assertEquals(q.take(_root_.com.histmersion.plugin.UpstreamQuota.TakePurpose("search")), Right(()))
    assertEquals(q.waitingUntil, None)

  /** A context as a plugin of SPI 2's first release wrote it: no quota of its own. */
  private object EarlyContext extends Plugin.Context:
    def http = Bodies
    def log = new Plugin.Log { def info(m: String) = (); def warn(m: String) = () }
    def env(name: String) = None
    def secret(s: Secret) = Left(_root_.com.histmersion.plugin.Plugin.Context.SecretResultPart1("none"))
    def secretVaults = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("no vaults"))

  test("a context written before budgets existed lends the unlimited quota for any upstream"):
    assertEquals(EarlyContext.quota(_root_.com.histmersion.plugin.Plugin.Context.QuotaUpstream("core")), UpstreamQuota.Unlimited)
    assertEquals(EarlyContext.quota(_root_.com.histmersion.plugin.Plugin.Context.QuotaUpstream("anything")).take(_root_.com.histmersion.plugin.UpstreamQuota.TakePurpose("lookup")), Right(()))

  test("a fetch context written before peers were told says no plugin is registered"):
    val ctx = new Fetch.Context:
      def memo[A](key: String)(compute: => A): A = compute
      val log = new Plugin.Log { def info(m: String) = (); def warn(m: String) = () }
    assert(!(ctx.registered(_root_.com.histmersion.plugin.Fetch.Context.RegisteredPluginId("oa-unpaywall"))).value)

  /** A catalogue as one written before re-reading existed: it re-reads nothing, and its parts stay read. */
  private object Floods extends EventCatalogue:
    def title = _root_.com.histmersion.plugin.EventCatalogue.TitleResult("Floods")
    def licence = Licence("CC0-1.0")
    def citation = _root_.com.histmersion.plugin.EventCatalogue.CitationResult("Floods")
    def home = _root_.com.histmersion.plugin.EventCatalogue.HomeResult("https://floods.example")
    def parts(query: CatalogueQuery) = List(_root_.com.histmersion.plugin.EventCatalogue.PartsResult("all"))
    def read(part: String, query: CatalogueQuery, http: Plugin.Http) = Right(CataloguePage(Nil))

  test("a catalogue re-reads nothing by default, and a part read once stays read"):
    val stored = CatalogueEvent(_root_.com.histmersion.plugin.CatalogueEvent.Id("f1"), _root_.com.histmersion.plugin.CatalogueEvent.Url("https://floods.example/f1"), _root_.com.histmersion.plugin.CatalogueEvent.Label("Flood"), _root_.com.histmersion.plugin.CatalogueEvent.Kind("flood"), Some(Year(-500)), Some(Year(-500)))
    assertEquals(Floods.reread(stored), None)
    assert(!(Floods.everyPartEachImport).value)

  // ── the AI provider's request options (the HTTP built-ins') ──────────────────────────────────

  test("a provider asks for no request option by default: no iteration notes, its failures prefixed as before"):
    val caps = AiCapabilities()
    assertEquals(({ val refactorTuple3 = (caps.iterationMarkers, caps.verbatimFailures); ((refactorTuple3._1).value, (refactorTuple3._2).value) }: (Boolean, Boolean)), (false, false))
    val early = new AiProvider:
      def title = _root_.com.histmersion.plugin.AiProvider.TitleResult("Early")
      def turn(request: AiTurn.Request, http: AiHttp) = Left(AiFailure.Error(_root_.com.histmersion.plugin.AiFailure.Error.Message("none")))
    assertEquals(early.capabilities, AiCapabilities(tools = true, structuredOutput = false))

  test("the members a provider or host compiled before them used still exist, with their old shapes"):
    val longs = Array.fill(4)(classOf[Long])
    assertEquals(new AiUsage(1, 2, 3, 4), AiUsage(_root_.com.histmersion.plugin.AiUsage.Input((1).toLong), _root_.com.histmersion.plugin.AiUsage.Output((2).toLong), _root_.com.histmersion.plugin.AiUsage.CacheRead((3).toLong), _root_.com.histmersion.plugin.AiUsage.CacheWrite((4).toLong), _root_.com.histmersion.plugin.AiUsage.Reasoning((0).toLong)))
    assertEquals(AiUsage(1, 2, 3, 4).reasoning, 0L)
    assert(classOf[AiUsage].getConstructor(longs*) != null)
    assert(AiUsage.getClass.getMethod("apply", longs*) != null)
    val bools = Array.fill(2)(classOf[Boolean])
    assertEquals(new AiCapabilities(false, true), AiCapabilities(_root_.com.histmersion.plugin.AiCapabilities.Tools(false), _root_.com.histmersion.plugin.AiCapabilities.StructuredOutput(true), _root_.com.histmersion.plugin.AiCapabilities.IterationMarkers(false), _root_.com.histmersion.plugin.AiCapabilities.VerbatimFailures(false)))
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
    val title = _root_.com.histmersion.plugin.MediaCollection.TitleResult("Portal objects")
    val role = _root_.com.histmersion.plugin.MediaItem.Role(MediaCollection.Object)
    def search(place: MediaPlace, http: Plugin.Http) =
      if place.label == "Nowhere" then Left(_root_.com.histmersion.plugin.MediaCollection.SearchResultPart1("offline"))
      else
        Right(
          List(
            Right(MediaItem(_root_.com.histmersion.plugin.MediaItem.Key(s"${place.id}-1"), place.label, _root_.com.histmersion.plugin.MediaItem.PageUrl("https://portal.example/1"), None, Left(_root_.com.histmersion.plugin.MediaItem.RightsPart1("closed")))),
            Left(_root_.com.histmersion.plugin.MediaCollection.SearchResultPart2Part1("one record unreadable"))
          )
        )

  test("an item built as SPI 2's first release built it has no role of its own and no rendition"):
    val rights = Left("not open access")
    val applied = MediaItem("k", "t", "https://p", None, rights, None, None, Nil, None, Nil, None)
    val constructed = new MediaItem("k", "t", "https://p", None, rights, None, None, Nil, None, Nil, Some("withheld"))
    assertEquals((applied.role, applied.rendition), (None, None))
    assertEquals((constructed.role, constructed.rendition, constructed.withheld), (None, None, Some("withheld")))
    assertEquals(applied, MediaItem(_root_.com.histmersion.plugin.MediaItem.Key("k"), _root_.com.histmersion.plugin.MediaItem.Title("t"), _root_.com.histmersion.plugin.MediaItem.PageUrl("https://p"), None, rights))

  test("a place described as SPI 2's first release described it carries no gazetteer ids"):
    val applied = MediaPlace("p", "Mycenae", None, None, None, None)
    val constructed = new MediaPlace("p", "Mycenae", None, None, None, None)
    assertEquals((applied.ids, constructed.ids), (Nil, Nil))
    assertEquals(applied, MediaPlace(_root_.com.histmersion.plugin.MediaPlace.Id("p"), _root_.com.histmersion.plugin.MediaPlace.Label("Mycenae")))

  test("a collection's new members by default: its role alone, places, no events, always asked, refusals final"):
    assertEquals(Portal.roles, Set(MediaCollection.Object))
    assertEquals(Portal.asks, Set(MediaTarget.Places))
    assertEquals(Portal.searchEvent(MediaEvent(_root_.com.histmersion.plugin.MediaEvent.Id("e"), _root_.com.histmersion.plugin.MediaEvent.Label("Fall of X")), Bodies), Right(Nil))
    assertEquals(Portal.check, Right(()))
    assertEquals(Portal.refusalBackoffMs, Nil)
    assertEquals(Portal.latestYear, None)
    assertEquals(Portal.placesCarrying, None)

  test("searchTargets by default: search for each place and searchEvent for each event, in order, failures counted"):
    val found = Portal.searchTargets(
      List(MediaPlace(_root_.com.histmersion.plugin.MediaPlace.Id("a"), _root_.com.histmersion.plugin.MediaPlace.Label("Mycenae")), MediaPlace(_root_.com.histmersion.plugin.MediaPlace.Id("b"), _root_.com.histmersion.plugin.MediaPlace.Label("Nowhere"))),
      List(MediaEvent(_root_.com.histmersion.plugin.MediaEvent.Id("e"), _root_.com.histmersion.plugin.MediaEvent.Label("Fall of X"))),
      Bodies
    )
    val (a, b, e) =
      (
        MediaTarget.Id(MediaTarget.Places, _root_.com.histmersion.plugin.MediaTarget.Id.Value("a")),
        MediaTarget.Id(MediaTarget.Places, _root_.com.histmersion.plugin.MediaTarget.Id.Value("b")),
        MediaTarget.Id(MediaTarget.Events, _root_.com.histmersion.plugin.MediaTarget.Id.Value("e"))
      )
    assertEquals(((found.uses.map(u => (u.target, u.item.key, u.rank))).map(refactorValue2 => { val refactorTuple4 = refactorValue2; (refactorTuple4._1, (refactorTuple4._2).value, (refactorTuple4._3).value) }): List[(com.histmersion.plugin.MediaTarget.Id, String, Int)]), List((a, "a-1", 0)))
    assertEquals(found.asked, List(a, b, e))
    assertEquals(found.unanswered, Set(b), "a failed search is not recorded as asked")
    assertEquals(((found.read).value: Int), 2)
    assertEquals(((found.failed).map(refactorValue3 => (refactorValue3).value): List[String]), List("a: one record unreadable", "Portal objects search for b: offline"))
    assertEquals((found.notTaken, found.categories), (Nil, Nil))

  test("completeAll by default: complete for each item, by its key"):
    val items =
      List(MediaItem(_root_.com.histmersion.plugin.MediaItem.Key("k1"), _root_.com.histmersion.plugin.MediaItem.Title("t"), _root_.com.histmersion.plugin.MediaItem.PageUrl("https://p"), None, Left(_root_.com.histmersion.plugin.MediaItem.RightsPart1("x"))), MediaItem(_root_.com.histmersion.plugin.MediaItem.Key("k2"), _root_.com.histmersion.plugin.MediaItem.Title("t"), _root_.com.histmersion.plugin.MediaItem.PageUrl("https://p"), None, Left(_root_.com.histmersion.plugin.MediaItem.RightsPart1("y"))))
    assertEquals(Portal.completeAll(items, Bodies), MediaCompleted(items.map(i => i.key -> i).toMap))
    val failing = new MediaCollection:
      val title = _root_.com.histmersion.plugin.MediaCollection.TitleResult("Failing")
      val role = _root_.com.histmersion.plugin.MediaItem.Role(MediaCollection.Map)
      def search(place: MediaPlace, http: Plugin.Http) = Right(Nil)
      override def complete(item: MediaItem, http: Plugin.Http) =
        if item.key == "k1" then Left(_root_.com.histmersion.plugin.MediaCollection.CompleteResultPart1("gone")) else Right(item)
    assertEquals(failing.completeAll(items, Bodies), MediaCompleted(Map({ val refactorTuple5 = "k2" -> items(1); (_root_.com.histmersion.plugin.MediaItem.Key(refactorTuple5._1), refactorTuple5._2) }), List(_root_.com.histmersion.plugin.MediaCompleted.Failed("k1: gone"))))

  test("the roles a collection's pictures may play are the ones the host stores"):
    assertEquals(
      MediaCollection.Roles,
      Set("object", "map", "image", "locator-map", "plan", "aerial-view", "route-map", "depiction")
    )

  test("a context written before the host lent its licence reading reads every statement as unknown"):
    val l = EarlyContext.licences
    assertEquals(l.readName(_root_.com.histmersion.plugin.LicenceReader.ReadNameName("CC BY 4.0")), Licence.Read(_root_.com.histmersion.plugin.Licence.Read.Id("CC BY 4.0"), _root_.com.histmersion.plugin.Licence.Read.Family(Licence.Read.Unknown)))
    assertEquals(
      l.readUrl(_root_.com.histmersion.plugin.LicenceReader.ReadUrlUrl("https://creativecommons.org/licenses/by/4.0")),
      Licence.Read.unknown(_root_.com.histmersion.plugin.Licence.Read.UnknownStatement("https://creativecommons.org/licenses/by/4.0"))
    )
    assertEquals(l.readFirst(List(_root_.com.histmersion.plugin.LicenceReader.ReadFirstStatements(" "), _root_.com.histmersion.plugin.LicenceReader.ReadFirstStatements("cc0"), _root_.com.histmersion.plugin.LicenceReader.ReadFirstStatements("pd"))), Licence.Read(_root_.com.histmersion.plugin.Licence.Read.Id("cc0"), _root_.com.histmersion.plugin.Licence.Read.Family(Licence.Read.Unknown)))
    assertEquals(l.readFirst(Nil), Licence.Read.unknown(_root_.com.histmersion.plugin.Licence.Read.UnknownStatement("")))
    assert(!(l.readName(_root_.com.histmersion.plugin.LicenceReader.ReadNameName("cc-by-4.0")).needsAttribution).value)
