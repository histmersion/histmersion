package com.histmersion
package plugin

import com.histmersion.util.problem.Text.sentence

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.lint.{tolerated, LintRule}

/** The SPI members added within SPI 2 for the first-party plugins — an answer's status and headers, an upstream's
  * budget, the peers of a fetch, a catalogue's re-reading and its changing parts, an AI provider's request options —
  * and their defaults, which keep a plugin or a host written before them compiling and linking.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SpiAdditionsSuite extends munit.FunSuite:

  private object Bodies extends Plugin.Http:
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def get(
        url: Plugin.Http.Url,
        headers: Map[Header.Name, Header.Value]
    ): Either[Problem, Plugin.HttpResponse.Body] =
      if (url).value.endsWith("/missing") then
        Left(
          Problem.Upstream.Http.here(
            Problem.Upstream.Name("x.example"),
            Problem.Upstream.Url(s"$url"),
            Problem.Upstream.Status.NotFound,
            External.ReplyExcerpt.head("no such page")
          )
        )
      else Right(Plugin.HttpResponse.Body(s"body of $url".getBytes("UTF-8")))

  test("Plugin.Http.fetch from a host that has only bodies: status 200, no headers, or the same Left"):
    val r = Bodies.fetch(Plugin.Http.Url("https://x.example/a")).fold(e => fail((e).sentence), identity)
    assertEquals(
      ({
        val refactorTuple7 = (r.status, r.headers, String((r.body).value, "UTF-8"));
        (
          (refactorTuple7._1).value,
          (refactorTuple7._2).iterator.map { case (refactorKey, refactorItem) =>
            ((refactorKey).value, (refactorItem).value)
          }.toMap,
          refactorTuple7._3
        )
      }: (Int, Map[String, String], String)),
      (200, Map.empty[String, String], "body of https://x.example/a")
    )
    assertEquals(
      (((Bodies
        .fetch(Plugin.Http.Url("https://x.example/missing"))
        .map(_.status))
        .left
        .map(refactorError => (refactorError).sentence))
        .map(refactorValue => (refactorValue).value): Either[String, Int]),
      Left("x.example answered HTTP 404 at https://x.example/missing — no such page")
    )

  test("a response's header is found in any case"):
    val r = Plugin.HttpResponse(
      Problem.Upstream.Status(429),
      Map {
        val refactorTuple = "x-ratelimit-remaining" -> "0";
        (Header.Name(refactorTuple._1), Header.Value(refactorTuple._2))
      },
      Plugin.HttpResponse.Body(Array.emptyByteArray)
    )
    assertEquals(
      ((r
        .header(Header.Name("X-RateLimit-Remaining")))
        .map(refactorValue7 => (refactorValue7).value): Option[String]),
      Some("0")
    )
    assertEquals(r.header(Header.Name("Retry-After")), None)

  test("the unlimited quota lets every call go and keeps nothing"):
    val q = UpstreamQuota.Unlimited
    assertEquals(List.fill(1000)(q.take(UpstreamQuota.TakePurpose("lookup"))).distinct, List(Right(())))
    q.observe(
      UpstreamQuota.ObserveStatus(429),
      Map {
        val refactorTuple2 = "retry-after" -> "3600";
        (Header.Name(refactorTuple2._1), Header.Value(refactorTuple2._2))
      }
    )
    assertEquals(q.take(UpstreamQuota.TakePurpose("search")), Right(()))
    assertEquals(q.waitingUntil, None)

  /** A context as a plugin of SPI 2's first release wrote it: no quota of its own. */
  private object EarlyContext extends Plugin.Context:
    def http = Bodies
    def log = new Plugin.Log {
      def info(m: Plugin.Log.Line): Unit = (); def warn(m: Plugin.Log.Line): Unit = ()
    }
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def env(name: EnvVar.Name): Option[EnvVar.Value] = None
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def secret(s: Secret): Either[Problem, Secret.Resolved] = Left(
      Problem.Plugin.Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("none"))
    )
    def secretVaults =
      Registry.refusing(Problem.Plugin.Vault(Problem.Plugin.Id("test"), Problem.Plugin.Retrieval.NoVault))

  test("a context written before budgets existed lends the unlimited quota for any upstream"):
    assertEquals(EarlyContext.quota(Plugin.Context.QuotaUpstream("core")), UpstreamQuota.Unlimited)
    assertEquals(
      EarlyContext.quota(Plugin.Context.QuotaUpstream("anything")).take(UpstreamQuota.TakePurpose("lookup")),
      Right(())
    )

  test("a fetch context written before peers were told says no plugin is registered"):
    val ctx = new Fetch.Context:
      def memo[A](key: Fetch.Context.MemoKey)(compute: => A): A = compute
      val log = new Plugin.Log {
        def info(m: Plugin.Log.Line): Unit = (); def warn(m: Plugin.Log.Line): Unit = ()
      }
    assert(!ctx.registered(Fetch.Context.RegisteredPluginId("oa-unpaywall")))

  /** A catalogue as one written before re-reading existed: it re-reads nothing, and its parts stay read. */
  private object Floods extends EventCatalogue:
    def title: EventCatalogue.Title = EventCatalogue.Title("Floods")
    def licence = Licence("CC0-1.0")
    def citation: EventCatalogue.Citation = EventCatalogue.Citation("Floods")
    def home: EventCatalogue.Home = EventCatalogue.Home("https://floods.example")
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def parts(query: CatalogueQuery): List[EventCatalogue.ReadPart] = List(EventCatalogue.ReadPart("all"))
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def read(
        part: EventCatalogue.ReadPart,
        query: CatalogueQuery,
        http: Plugin.Http
    ): Either[Problem, CataloguePage] = Right(CataloguePage(Nil))

  test("a catalogue re-reads nothing by default, and a part read once stays read"):
    val stored = CatalogueEvent(
      CatalogueEvent.Id("f1"),
      CatalogueEvent.Url("https://floods.example/f1"),
      CatalogueEvent.Label("Flood"),
      CatalogueEvent.Kind("flood"),
      Some(Year(-500)),
      Some(Year(-500))
    )
    assertEquals(Floods.reread(stored), None)
    assert(!(Floods.everyPartEachImport).value)

  // ── the AI provider's request options (the HTTP built-ins') ──────────────────────────────────

  test("a provider asks for no request option by default: no iteration notes, its failures prefixed as before"):
    val caps = AiCapabilities()
    assertEquals(
      ({
        val refactorTuple3 = (caps.iterationMarkers, caps.verbatimFailures);
        ((refactorTuple3._1).value, (refactorTuple3._2).value)
      }: (Boolean, Boolean)),
      (false, false)
    )
    val early = new AiProvider:
      def title = AiProvider.Title("Early")
      def turn(request: AiTurn.Request, http: AiHttp) = Left(
        Problem.Plugin.Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("none"))
      )
    assertEquals(
      early.capabilities,
      AiCapabilities(tools = AiCapabilities.Tools(true), structuredOutput = AiCapabilities.StructuredOutput(false))
    )

  test("the members a provider or host compiled before them used still exist, with their old shapes"):
    val longs = Array.fill(4)(classOf[Long])
    assertEquals(
      new AiUsage(1, 2, 3, 4),
      AiUsage(
        AiUsage.Input((1).toLong),
        AiUsage.Output((2).toLong),
        AiUsage.CacheRead((3).toLong),
        AiUsage.CacheWrite((4).toLong),
        AiUsage.Reasoning((0).toLong)
      )
    )
    assertEquals(
      ((AiUsage(
        AiUsage.Input((1).toLong),
        AiUsage.Output((2).toLong),
        AiUsage.CacheRead((3).toLong),
        AiUsage.CacheWrite((4).toLong)
      ).reasoning).value: Long),
      0L
    )
    assert(classOf[AiUsage].getConstructor(longs*) != null)
    assert(AiUsage.getClass.getMethod("apply", longs*) != null)
    val bools = Array.fill(2)(classOf[Boolean])
    assertEquals(
      new AiCapabilities(false, true),
      AiCapabilities(
        AiCapabilities.Tools(false),
        AiCapabilities.StructuredOutput(true),
        AiCapabilities.IterationMarkers(false),
        AiCapabilities.VerbatimFailures(false)
      )
    )
    assert(classOf[AiCapabilities].getConstructor(bools*) != null)
    assert(AiCapabilities.getClass.getMethod("apply", bools*) != null)
    val old = AiTurn.Request(
      AiTurn.Request.Model("m"),
      AiTurn.Request.System("s"),
      Nil,
      Nil,
      (Some("{}")).map(refactorValue4 => AiTurn.Request.AnswerSchema(refactorValue4)),
      AiTurn.Request.MaxTokens(10),
      AiTurn.Request.Iteration(1),
      AiTurn.Request.MaxIterations(2)
    )
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
    val title = MediaCollection.Title("Portal objects")
    val role = MediaCollection.Object
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def search(place: MediaPlace, http: Plugin.Http) =
      if place.label == MediaPlace.Label("Nowhere") then
        Left(Problem.Config.Unavailable(Problem.Config.Capability.Network))
      else
        Right(
          List(
            Right(
              MediaItem(
                MediaItem.Key(s"${place.id}-1"),
                MediaItem.Title((place.label).value),
                MediaItem.PageUrl("https://portal.example/1"),
                None,
                Left(
                  Problem.Plugin
                    .Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("closed"))
                )
              )
            ),
            Left(
              Problem.Plugin.Said(
                Problem.Plugin.Id("test"),
                Problem.Plugin.Claim.Failed,
                External.PluginSaid.of("one record unreadable")
              )
            )
          )
        )

  test("an item built from its required fields has no role of its own and no rendition"):
    val item = MediaItem(
      MediaItem.Key("k"),
      MediaItem.Title("t"),
      MediaItem.PageUrl("https://p"),
      None,
      Left(
        Problem.Plugin
          .Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("not open access"))
      )
    )
    assertEquals((item.role, item.rendition, item.withheld, item.ids), (None, None, None, Nil))

  test("a place described by its required fields carries no gazetteer ids"):
    assertEquals(MediaPlace(MediaPlace.Id("p"), MediaPlace.Label("Mycenae")).ids, Nil)

  test("a collection's new members by default: its role alone, places, no events, always asked, refusals final"):
    assertEquals(Portal.roles, Set(MediaCollection.Object))
    assertEquals(Portal.asks, Set(MediaTarget.Places))
    assertEquals(Portal.searchEvent(MediaEvent(MediaEvent.Id("e"), MediaEvent.Label("Fall of X")), Bodies), Right(Nil))
    assertEquals(Portal.check, Right(()))
    assertEquals(Portal.refusalBackoffMs, Nil)
    assertEquals(Portal.latestYear, None)
    assertEquals(Portal.placesCarrying, None)

  test("searchTargets by default: search for each place and searchEvent for each event, in order, failures counted"):
    val found = Portal.searchTargets(
      List(
        MediaPlace(MediaPlace.Id("a"), MediaPlace.Label("Mycenae")),
        MediaPlace(MediaPlace.Id("b"), MediaPlace.Label("Nowhere"))
      ),
      List(MediaEvent(MediaEvent.Id("e"), MediaEvent.Label("Fall of X"))),
      Bodies
    )
    val (a, b, e) =
      (
        MediaTarget.Id(MediaTarget.Places, MediaTarget.Id.Value("a")),
        MediaTarget.Id(MediaTarget.Places, MediaTarget.Id.Value("b")),
        MediaTarget.Id(MediaTarget.Events, MediaTarget.Id.Value("e"))
      )
    assertEquals(
      ((found.uses.map(u => (u.target, u.item.key, u.rank))).map { refactorValue2 =>
        val refactorTuple4 = refactorValue2; (refactorTuple4._1, (refactorTuple4._2).value, (refactorTuple4._3).value)
      }: List[(MediaTarget.Id, String, Int)]),
      List((a, "a-1", 0))
    )
    assertEquals(found.asked, List(a, b, e))
    assertEquals(found.unanswered, Set(b), "a failed search is not recorded as asked")
    assertEquals(((found.read).value: Int), 2)
    assertEquals(
      found.failed.map(f => s"${f.line}"),
      List("a: test (failed): one record unreadable", "Portal objects search for b: this server has no network")
    )
    assertEquals((found.notTaken, found.categories), (Nil, Nil))

  test("completeAll by default: complete for each item, by its key"):
    val items =
      List(
        MediaItem(
          MediaItem.Key("k1"),
          MediaItem.Title("t"),
          MediaItem.PageUrl("https://p"),
          None,
          Left(Problem.Plugin.Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("x")))
        ),
        MediaItem(
          MediaItem.Key("k2"),
          MediaItem.Title("t"),
          MediaItem.PageUrl("https://p"),
          None,
          Left(Problem.Plugin.Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("y")))
        )
      )
    assertEquals(Portal.completeAll(items, Bodies), MediaCompleted(items.map(i => i.key -> i).toMap))
    val failing = new MediaCollection:
      val title = MediaCollection.Title("Failing")
      val role = MediaCollection.Map
      def search(place: MediaPlace, http: Plugin.Http) = Right(Nil)
      override def complete(item: MediaItem, http: Plugin.Http) =
        if item.key == MediaItem.Key("k1") then
          Left(
            Problem.Plugin.Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("gone"))
          )
        else Right(item)
    assertEquals(
      failing.completeAll(items, Bodies),
      MediaCompleted(
        Map { val refactorTuple5 = "k2" -> items(1); (MediaItem.Key(refactorTuple5._1), refactorTuple5._2) },
        List(
          MediaFound.Failed(
            MediaFound.Failed.About("k1"),
            Problem.Plugin.Said(Problem.Plugin.Id("test"), Problem.Plugin.Claim.Failed, External.PluginSaid.of("gone"))
          )
        )
      )
    )

  test("the roles a collection's pictures may play are the ones the host stores"):
    assertEquals(
      ((MediaCollection.Roles).map(refactorValue9 => (refactorValue9).value): Set[String]),
      Set("object", "map", "image", "locator-map", "plan", "aerial-view", "route-map", "depiction")
    )

  test("a context written before the host lent its licence reading reads every statement as unknown"):
    val l = EarlyContext.licences
    assertEquals(
      l.readName(LicenceReader.ReadNameName("CC BY 4.0")),
      Licence.Read(Licence.Read.Id("CC BY 4.0"), Licence.Read.Family(Licence.Read.Unknown))
    )
    assertEquals(
      l.readUrl(LicenceReader.ReadUrlUrl("https://creativecommons.org/licenses/by/4.0")),
      Licence.Read.unknown(Licence.Read.UnknownStatement("https://creativecommons.org/licenses/by/4.0"))
    )
    assertEquals(
      l.readFirst(
        List(
          LicenceReader.ReadFirstStatements(" "),
          LicenceReader.ReadFirstStatements("cc0"),
          LicenceReader.ReadFirstStatements("pd")
        )
      ),
      Licence.Read(Licence.Read.Id("cc0"), Licence.Read.Family(Licence.Read.Unknown))
    )
    assertEquals(l.readFirst(Nil), Licence.Read.unknown(Licence.Read.UnknownStatement("")))
    assert(!(l.readName(LicenceReader.ReadNameName("cc-by-4.0")).needsAttribution).value)
