package com.histmersion.db

import com.histmersion.domain.library.sources.SourceRepo

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.values.{PlaceKind, SourceStatus}

/** The time rule of the place views (2026-10-09, [[PlaceTimeBasis]]) against a real PostGIS (HISTMERSION_TEST_DB_URL;
  * skipped otherwise), on every surface that reads places at a time: the map feed, search and suggestions, a place's
  * children and its subtree, and the place itself.
  *
  * One fixture per case, under one shapeless root: a dated site that overlaps 2500 BCE and one that does not; an
  * undated site with no finding, one with a finding dated then, one with only an undated finding, one with an event
  * dated 1500 BCE; a river dated by its attestation only; a present-day city; a place whose start is unknown (a stored
  * window). Fixtures sit at 100–109°E, 55°S — a degree (≈ 64 km) apart, beyond the point tolerance — where no other
  * suite has any; ids start with `zqtv`.
  */
class PlaceTimeSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private val Lat = -55.0
  private def pt(i: Int) = Some(s"""{"type":"Point","coordinates":[${100 + i}.0,$Lat]}""")

  private def place(id: String, kind: String, i: Int, period: Option[Period], props: Option[String] = None) =
    PlaceInput(
      Place(id, s"Zqtv ${id.stripPrefix("zqtv_")}", kind, pt(i), period, Some("fixture"), props),
      List(PlaceName(s"Zqtv ${id.stripPrefix("zqtv_")}")),
      List(PlaceRelation(id, "zqtv_root"))
    )

  private val fixtures = List(
    PlaceInput(Place("zqtv_root", "Zqtv root", PlaceKind.Region), List(PlaceName("Zqtv root"))),
    place("zqtv_dated", PlaceKind.Site, 0, Some(Period(-2600, -2400))),
    place("zqtv_late", PlaceKind.Site, 1, Some(Period(1100, 1300))),
    place("zqtv_bare", PlaceKind.Site, 2, None),
    place("zqtv_evid", PlaceKind.Site, 3, None),
    place("zqtv_undfind", PlaceKind.Site, 4, None),
    place("zqtv_evt", PlaceKind.Site, 5, None),
    place("zqtv_river", PlaceKind.Feature, 6, Some(Period(-30, 2100))),
    place("zqtv_city", PlaceKind.Site, 7, None, Some("""{"modern":true}""")),
    place("zqtv_su", PlaceKind.Site, 8, Some(Period(1469, 1769)))
  )

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM events WHERE id LIKE 'zqtv%'".update.run()
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zqtv%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zqtv%'".update.run()

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      val _ = PlaceFixtures.upsertPlaces(ds, fixtures)
      // an unknown start (V074): the stored period is the window the time filters use
      connect(ds)(sql"UPDATE places SET start_unknown = true WHERE id = 'zqtv_su'".update.run())
      def f(i: Int, at: Int, p: Option[Period]) =
        Finding(s"zqtv_src#$i", "zqtv_src", s"claim $i", None, Some(GeoPoint(100.0 + at, Lat)), p, None, Nil, None)
      SourceRepo.upsert(
        ds,
        Source(
          citekey = "zqtv_src",
          title = "Time rule fixture",
          authors = List("T"),
          doi = None,
          year = Some(2026),
          venue = None,
          status = SourceStatus.Current,
          topics = Nil,
          regions = Nil,
          tags = Nil,
          period = None,
          summary = None,
          citedByCount = None,
          influentialCitations = None,
          provenance = None,
          calibreId = None,
          findings = List(f(0, 3, Some(Period(-2550, -2450))), f(1, 4, None))
        ),
        cfg.limits.chunkChars,
        cfg.places.pointToleranceKm
      )
      EventFixtures.put(
        ds,
        "zqtv_event",
        EventPut(
          "Zqtv siege",
          timeSpan = TimeSpan(notBefore = Some(-1520), notAfter = Some(-1480)),
          placeId = Some("zqtv_evt")
        )
      )
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private val Ours = "\"id\":\"(zqtv_[a-z]+)\"".r

  private def onMap(kind: String, asOf: Option[Long]): Set[String] =
    Ours.findAllMatchIn(PlaceFixtures.geoJson(ds, Some(kind), asOf, 100000)).map(_.group(1)).toSet
  private def found(asOf: Option[Long]): Set[String] = PlaceFixtures.search(ds, Some("Zqtv"), asOf, 100).map(_.id).toSet
  private def kids(asOf: Option[Long]): Set[String] = PlaceFixtures.children(ds, "zqtv_root", asOf, 100).map(_.id).toSet
  private def tree(asOf: Option[Long]): Set[String] =
    PlaceFixtures.hierarchy(ds, asOf, Some("zqtv_root"), 3, 100).map(_.id).toSet

  private val All = fixtures.map(_.place.id).toSet

  test("the fixtures' findings and event are where the cases need them"):
    assertEquals(PlaceFixtures.placesOf(ds, "zqtv_src#0").map(_.id).toSet, Set("zqtv_evid"))
    assertEquals(PlaceFixtures.placesOf(ds, "zqtv_src#1").map(_.id).toSet, Set("zqtv_undfind"))

  test(
    "at 2500 BCE every surface shows the dated overlap, the river, the root, and the undated site with a finding then"
  ):
    val sites = Set("zqtv_dated", "zqtv_evid")
    assertEquals(onMap("site", Some(-2500L)), sites)
    assertEquals(onMap("feature", Some(-2500L)), Set("zqtv_river"), "dated 30 BCE–2100 CE, yet a river is there")
    val listed = sites + "zqtv_river"
    assertEquals(found(Some(-2500L)), listed + "zqtv_root", "a shapeless undated root is a grouping node")
    assertEquals(kids(Some(-2500L)), listed)
    assertEquals(tree(Some(-2500L)), listed + "zqtv_root")

  test("an undated place with only an undated finding, or no finding, is in no time view"):
    for y <- List(-2500L, -1500L, 500L, 1200L, 1600L) do
      assert(!found(Some(y)).exists(Set("zqtv_bare", "zqtv_undfind")), s"$y")
      assert(!onMap("site", Some(y)).exists(Set("zqtv_bare", "zqtv_undfind")), s"$y")
      assert(!kids(Some(y)).exists(Set("zqtv_bare", "zqtv_undfind")), s"$y")

  test("an event dated within the window counts as evidence"):
    assert(onMap("site", Some(-1500L)).contains("zqtv_evt"))
    assert(kids(Some(-1500L)).contains("zqtv_evt"))
    assert(!onMap("site", Some(-2500L)).contains("zqtv_evt"))

  test("at 1200 CE: the late site; the 2500 BCE ones are gone, findings or not; no city yet"):
    assertEquals(onMap("site", Some(1200L)), Set("zqtv_late"))
    assertEquals(kids(Some(1200L)), Set("zqtv_late", "zqtv_river"))

  test("a present-day city appears from places.modern-from; an unknown start uses its window"):
    assertEquals(onMap("site", Some(1600L)), Set("zqtv_city", "zqtv_su"))
    assert(!found(Some(1499L)).contains("zqtv_city"))
    assert(found(Some(1500L)).contains("zqtv_city"))
    assert(found(Some(1500L)).contains("zqtv_su"))
    assert(!found(Some(1400L)).contains("zqtv_su"), "before the window of its unknown start")
    assertEquals(
      PlaceFixtures.search(ds, Some("Zqtv city"), Some(1499L), 10, modernFrom = 1400L).map(_.id).toList,
      List("zqtv_city"),
      "the cutoff is the caller's (configured) value"
    )

  test("no time: nothing changes — every place is listed, the map across all time draws no present-day place"):
    assertEquals(found(None), All)
    assertEquals(kids(None), All - "zqtv_root")
    assertEquals(tree(None), All)
    assertEquals(onMap("site", None), All -- Set("zqtv_root", "zqtv_river", "zqtv_city"))

  test("the map feed says on what ground: findings for the undated site, dated for the rest"):
    val json = PlaceFixtures.geoJson(ds, Some("site"), Some(-2500L), 100000)
    def basisOf(id: String) = s"\"id\":\"$id\"[^}]*\"basis\":\"([a-z]+)\"".r.findFirstMatchIn(json).map(_.group(1))
    assertEquals(basisOf("zqtv_evid"), Some("findings"))
    assertEquals(basisOf("zqtv_dated"), Some("dated"))

  test("among points, dated places outrank the rest under the limit"):
    val json = PlaceFixtures.geoJson(ds, Some("site"), Some(1600L), 100000)
    val order = Ours.findAllMatchIn(json).map(_.group(1)).toList
    assertEquals(order, List("zqtv_su", "zqtv_city"))

  test("the SQL basis is the pure one, place by place; the card notes a place dated by its findings"):
    def attrs(id: String) = connect(ds)(
      sql"""SELECT kind, coalesce((properties->>'modern')::boolean, false), lower_inf(period) AND upper_inf(period),
                   geom IS NOT NULL FROM places WHERE id = $id""".query[(String, Boolean, Boolean, Boolean)].run().head
    )
    for id <- All do
      val (kind, modern, undated, geom) = attrs(id)
      val d = PlaceFixtures.get(ds, id, Some(-2500L)).get
      assertEquals(d.timeBasis, PlaceTimeBasis.of(kind, modern, imported = false, undated, geom), id)
    assert(PlaceFixtures.get(ds, "zqtv_evid", Some(-2500L)).get.datedByFindings)
    assert(!PlaceFixtures.get(ds, "zqtv_evid", None).get.datedByFindings)
    assertEquals(
      PlaceFixtures.get(ds, "zqtv_root", Some(-2500L)).get.children.map(_.id).toSet,
      Set("zqtv_dated", "zqtv_evid", "zqtv_river"),
      "the place's own children follow the rule too"
    )

  test("V076 dates a stored Cliopatria concept from its slices and leaves one with no dated slice alone"):
    connect(ds):
      val _ = sql"""INSERT INTO places (id, label, kind, period, source) VALUES
                      ('cliopatria_zqtv_kingdom', 'zqtv kingdom', 'polity', int8range(NULL, NULL), 'Cliopatria (Seshat Databank), CC BY 4.0'),
                      ('cliopatria_zqtv_kingdom_-900_-801', 'Zqtv Kingdom', 'polity', int8range(-900, -801, '[]'), 'Cliopatria (Seshat Databank), CC BY 4.0'),
                      ('cliopatria_zqtv_kingdom_-700_-601', 'Zqtv Kingdom', 'polity', int8range(-700, -601, '[]'), 'Cliopatria (Seshat Databank), CC BY 4.0'),
                      ('cliopatria_zqtv_nodates', 'zqtv nodates', 'polity', int8range(NULL, NULL), 'Cliopatria (Seshat Databank), CC BY 4.0'),
                      ('cliopatria_zqtv_nodates_3', 'Zqtv Nodates', 'polity', int8range(NULL, NULL), 'Cliopatria (Seshat Databank), CC BY 4.0')
                    ON CONFLICT (id) DO NOTHING""".update.run()
      val _ = sql"""INSERT INTO place_relations (child_id, parent_id, kind, period) VALUES
                      ('cliopatria_zqtv_kingdom_-900_-801', 'cliopatria_zqtv_kingdom', 'part_of', int8range(-900, -801, '[]')),
                      ('cliopatria_zqtv_kingdom_-700_-601', 'cliopatria_zqtv_kingdom', 'part_of', int8range(-700, -601, '[]')),
                      ('cliopatria_zqtv_nodates_3', 'cliopatria_zqtv_nodates', 'part_of', int8range(NULL, NULL))
                    ON CONFLICT DO NOTHING""".update.run()
    try
      val migration = String(
        getClass.getClassLoader
          .getResourceAsStream("db/migration/V076__place_time_rule.sql")
          .readAllBytes(),
        java.nio.charset.StandardCharsets.UTF_8
      )
      connect(ds)(scala.util.Using.resource(summon[DbCon].connection.createStatement())(_.execute(migration)))
      def span(id: String) = PlaceFixtures.get(ds, id).map(d => (d.place.periodStart, d.place.periodEnd))
      assertEquals(span("cliopatria_zqtv_kingdom"), Some((Some(-900L), Some(-601L))))
      assertEquals(span("cliopatria_zqtv_nodates"), Some((None, None)))
      assert(
        !PlaceFixtures.search(ds, Some("zqtv kingdom"), Some(-2500L), 10).exists(_.id == "cliopatria_zqtv_kingdom"),
        "a dated concept is no longer in every time view"
      )
    finally connect(ds)(sql"DELETE FROM places WHERE id LIKE 'cliopatria_zqtv%'".update.run())
