package com.histmersion
package plugin

/** The media-collection and event-catalogue parts of the SPI (docs/research-plugins.md §6). */
class ProviderSpiSuite extends munit.FunSuite:

  private val offline = new Plugin.Http {
    def get(
        url: Plugin.Http.Url,
        headers: Map[Header.Name, Header.Value]
    ): Either[Problem, Plugin.HttpResponse.Body] = Left(Problem("offline"))
  }

  private object Portal extends MediaCollection:
    val title = MediaCollection.Title("Portal objects")
    val role = MediaCollection.Object
    def search(place: MediaPlace, http: Plugin.Http) =
      Right(
        List(
          Right(
            MediaItem(
              MediaItem.Key("k"),
              MediaItem.Title((place.label).value),
              MediaItem.PageUrl("https://portal.example/k"),
              None,
              Left(Problem("not open access"))
            )
          )
        )
      )

  private object Quakes extends EventCatalogue:
    val title = EventCatalogue.Title("Quakes"); val licence = Licence("CC0-1.0");
    val citation = EventCatalogue.Citation("Q"); val home = EventCatalogue.Home("https://q.example")
    def parts(q: CatalogueQuery): List[EventCatalogue.ReadPart] = List(EventCatalogue.ReadPart(s"to:${q.to}"))
    def read(
        part: EventCatalogue.ReadPart,
        q: CatalogueQuery,
        http: Plugin.Http
    ): Either[Problem, CataloguePage] = Right(CataloguePage(Nil))

  test("a collection's defaults: the museum rule, three per place, a second between requests, complete = as is"):
    assertEquals(
      ({
        val refactorTuple = (Portal.matchRule, Portal.perPlace, Portal.requestIntervalMs, Portal.description);
        (refactorTuple._1, (refactorTuple._2).value, (refactorTuple._3).value, (refactorTuple._4).value)
      }: (MediaMatchRule, Int, Int, String)),
      (MediaMatchRule.Museum, 3, 1000, "")
    )
    val item =
      Portal.search(MediaPlace(MediaPlace.Id("p"), MediaPlace.Label("Mycenae")), offline).toOption.get.head.toOption.get
    assertEquals(Portal.complete(item, offline), Right(item))
    assertEquals(
      ((item.rights).left.map(refactorError => (refactorError).value): Either[String, MediaRights]),
      Left("not open access")
    )
    assertEquals(
      ((MediaCollection.Roles).map(refactorValue2 => (refactorValue2).value): Set[String]),
      Set("object", "map", "image", "locator-map", "plan", "aerial-view", "route-map", "depiction")
    )

  test("a catalogue's defaults and its parts for a query"):
    assertEquals(((Quakes.requestIntervalMs).value: Int), 1000)
    assertEquals(Quakes.floorYear, None)
    assertEquals(
      ((Quakes
        .parts(CatalogueQuery(Some(Year(-3000)), Year(2026))))
        .map(refactorValue => (refactorValue).value): List[String]),
      List("to:2026")
    )
    val e = CatalogueEvent(
      CatalogueEvent.Id("1"),
      CatalogueEvent.Url("u"),
      CatalogueEvent.Label("Quake"),
      CatalogueEvent.Kind("earthquake"),
      Some(Year(-1200)),
      Some(Year(-1200))
    )
    assertEquals(
      ({
        val refactorTuple2 = (e.historicity, e.beginBy, e.endFrom);
        ((refactorTuple2._1).value, refactorTuple2._2, refactorTuple2._3)
      }: (String, Option[Year], Option[Year])),
      ("attested", None, None)
    )

  test("a context written before media and events existed still compiles and refuses both"):
    val ctx = new Plugin.Context:
      def http = offline
      def log = new Plugin.Log {
        def info(m: Plugin.Log.InfoMessage): Unit = (); def warn(m: Plugin.Log.WarnMessage): Unit = ()
      }
      def env(name: EnvVar.Name): Option[EnvVar.Value] = None
      def secret(s: Secret): Either[Problem, Secret.Resolved] =
        Left(Problem("none"))
      def secretVaults = Registry.refusing(Registry.RefusingWhy("no vaults"))
    assert(
      ctx.mediaCollections.add(Registry.AddId("portal"), Portal).left.exists((_).value.contains("no media import"))
    )
    assert(
      ctx.eventCatalogues.add(Registry.AddId("quakes"), Quakes).left.exists((_).value.contains("no event catalogues"))
    )
    assertEquals(Spi.Version, 2)
