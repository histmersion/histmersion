package com.histmersion.plugin

/** The media-collection and event-catalogue parts of the SPI (docs/research-plugins.md §6). */
class ProviderSpiSuite extends munit.FunSuite:

  private val offline = new Plugin.Http { def get(url: _root_.com.histmersion.plugin.Plugin.Http.GetUrl, headers: Map[_root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart1, _root_.com.histmersion.plugin.Plugin.Http.GetHeadersPart2]): Either[_root_.com.histmersion.plugin.Plugin.Http.GetResultPart1, _root_.com.histmersion.plugin.Plugin.HttpResponse.Body] = Left(_root_.com.histmersion.plugin.Plugin.Http.GetResultPart1("offline")) }

  private object Portal extends MediaCollection:
    val title = _root_.com.histmersion.plugin.MediaCollection.TitleResult("Portal objects")
    val role = _root_.com.histmersion.plugin.MediaItem.Role(MediaCollection.Object)
    def search(place: MediaPlace, http: Plugin.Http) =
      Right(List(Right(MediaItem(_root_.com.histmersion.plugin.MediaItem.Key("k"), _root_.com.histmersion.plugin.MediaItem.Title((place.label).value), _root_.com.histmersion.plugin.MediaItem.PageUrl("https://portal.example/k"), None, Left(_root_.com.histmersion.plugin.MediaItem.RightsPart1("not open access"))))))

  private object Quakes extends EventCatalogue:
    val title = _root_.com.histmersion.plugin.EventCatalogue.TitleResult("Quakes"); val licence = Licence("CC0-1.0"); val citation = _root_.com.histmersion.plugin.EventCatalogue.CitationResult("Q"); val home = _root_.com.histmersion.plugin.EventCatalogue.HomeResult("https://q.example")
    def parts(q: CatalogueQuery): List[_root_.com.histmersion.plugin.EventCatalogue.PartsResult] = List(_root_.com.histmersion.plugin.EventCatalogue.PartsResult(s"to:${q.to}"))
    def read(part: _root_.com.histmersion.plugin.EventCatalogue.ReadPart, q: CatalogueQuery, http: Plugin.Http): Either[_root_.com.histmersion.plugin.EventCatalogue.ReadResultPart1, CataloguePage] = Right(CataloguePage(Nil))

  test("a collection's defaults: the museum rule, three per place, a second between requests, complete = as is"):
    assertEquals(
      ({ val refactorTuple = (Portal.matchRule, Portal.perPlace, Portal.requestIntervalMs, Portal.description); (refactorTuple._1, (refactorTuple._2).value, (refactorTuple._3).value, (refactorTuple._4).value) }: (com.histmersion.plugin.MediaMatchRule, Int, Int, String)),
      (MediaMatchRule.Museum, 3, 1000, "")
    )
    val item = Portal.search(MediaPlace(_root_.com.histmersion.plugin.MediaPlace.Id("p"), _root_.com.histmersion.plugin.MediaPlace.Label("Mycenae")), offline).toOption.get.head.toOption.get
    assertEquals(Portal.complete(item, offline), Right(item))
    assertEquals(((item.rights).left.map(refactorError => (refactorError).value): Either[String, com.histmersion.plugin.MediaRights]), Left("not open access"))
    assertEquals(
      ((MediaCollection.Roles).map(refactorValue2 => (refactorValue2).value): Set[String]),
      Set("object", "map", "image", "locator-map", "plan", "aerial-view", "route-map", "depiction")
    )

  test("a catalogue's defaults and its parts for a query"):
    assertEquals(((Quakes.requestIntervalMs).value: Int), 1000)
    assertEquals(Quakes.floorYear, None)
    assertEquals(((Quakes.parts(CatalogueQuery(Some(Year(-3000)), Year(2026)))).map(refactorValue => (refactorValue).value): List[String]), List("to:2026"))
    val e = CatalogueEvent(_root_.com.histmersion.plugin.CatalogueEvent.Id("1"), _root_.com.histmersion.plugin.CatalogueEvent.Url("u"), _root_.com.histmersion.plugin.CatalogueEvent.Label("Quake"), _root_.com.histmersion.plugin.CatalogueEvent.Kind("earthquake"), Some(Year(-1200)), Some(Year(-1200)))
    assertEquals(({ val refactorTuple2 = (e.historicity, e.beginBy, e.endFrom); ((refactorTuple2._1).value, refactorTuple2._2, refactorTuple2._3) }: (String, Option[com.histmersion.plugin.Year], Option[com.histmersion.plugin.Year])), ("attested", None, None))

  test("a context written before media and events existed still compiles and refuses both"):
    val ctx = new Plugin.Context:
      def http = offline
      def log = new Plugin.Log { def info(m: _root_.com.histmersion.plugin.Plugin.Log.InfoMessage): Unit = (); def warn(m: _root_.com.histmersion.plugin.Plugin.Log.WarnMessage): Unit = () }
      def env(name: _root_.com.histmersion.plugin.Plugin.Context.EnvName): _root_.scala.Option[_root_.com.histmersion.plugin.Plugin.Context.EnvResult] = None
      def secret(s: Secret): Either[_root_.com.histmersion.plugin.Plugin.Context.SecretResultPart1, _root_.com.histmersion.plugin.Plugin.Context.SecretResultPart2] = Left(_root_.com.histmersion.plugin.Plugin.Context.SecretResultPart1("none"))
      def secretVaults = Registry.refusing(_root_.com.histmersion.plugin.Registry.RefusingWhy("no vaults"))
    assert(ctx.mediaCollections.add(_root_.com.histmersion.plugin.Registry.AddId("portal"), Portal).left.exists((_).value.contains("no media import")))
    assert(ctx.eventCatalogues.add(_root_.com.histmersion.plugin.Registry.AddId("quakes"), Quakes).left.exists((_).value.contains("no event catalogues")))
    assertEquals(Spi.Version, 2)
