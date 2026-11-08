package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.{GeoPoint, TimeSpan}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.catalogue.Place.Id as PlaceId
import com.histmersion.domain.catalogue.Place.Kind.Wire as KindWire

import com.augustnagro.magnum.*
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{EventPut, Period, Place}
import com.histmersion.api.library.{Finding, Source}
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.EventSummary

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
class PlaceTimeSuite extends DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private val Lat = Latitude(-55.0)
  private def pt(i: Int) = Some(Place.Geometry(s"""{"type":"Point","coordinates":[${100 + i}.0,$Lat]}"""))

  private def place(
      id: Place.Id,
      kind: Place.KindValue,
      i: Int,
      period: Option[Period],
      props: Option[Place.Properties] = None
  ) =
    Place.Input(
      Place(id, Place.Label(s"Zqtv ${id.value.stripPrefix("zqtv_")}"), kind, pt(i), period, Some(Place.Source("fixture")), props),
      List(Place.Name(Place.Name.Value(s"Zqtv ${id.value.stripPrefix("zqtv_")}"))),
      List(Place.Relation(Place.Relation.ChildId(id.value), Place.Relation.ParentId("zqtv_root")))
    )

  private val fixtures = List(
    Place.Input(
      Place(Place.Id("zqtv_root"), Place.Label("Zqtv root"), Place.KindValue(Place.Kind.Region)),
      List(Place.Name(Place.Name.Value("Zqtv root")))
    ),
    place(Place.Id("zqtv_dated"), Place.KindValue(Place.Kind.Site), 0, Some(Period(Year(-2600), Year(-2400)))),
    place(Place.Id("zqtv_late"), Place.KindValue(Place.Kind.Site), 1, Some(Period(Year(1100), Year(1300)))),
    place(Place.Id("zqtv_bare"), Place.KindValue(Place.Kind.Site), 2, None),
    place(Place.Id("zqtv_evid"), Place.KindValue(Place.Kind.Site), 3, None),
    place(Place.Id("zqtv_undfind"), Place.KindValue(Place.Kind.Site), 4, None),
    place(Place.Id("zqtv_evt"), Place.KindValue(Place.Kind.Site), 5, None),
    place(Place.Id("zqtv_river"), Place.KindValue(Place.Kind.Feature), 6, Some(Period(Year(-30), Year(2100)))),
    place(Place.Id("zqtv_city"), Place.KindValue(Place.Kind.Site), 7, None, Some(Place.Properties("""{"modern":true}"""))),
    place(Place.Id("zqtv_su"), Place.KindValue(Place.Kind.Site), 8, Some(Period(Year(1469), Year(1769))))
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
        Finding(Finding.Id(s"zqtv_src#$i"), Citekey("zqtv_src"), Finding.Claim(s"claim $i"), None, Some(GeoPoint(Longitude(100.0 + at), Lat)), p, None, Nil, None)
      SourceFixtures.upsert(
        ds,
        Source(
          citekey = Citekey("zqtv_src"),
          title = com.histmersion.api.library.Title("Time rule fixture"),
          authors = (List("T")).map(com.histmersion.api.library.AuthorName.apply),
          doi = None,
          year = (Some(2026)).map(Year.apply),
          venue = None,
          status = Source.Status.Current,
          topics = Nil,
          regions = Nil,
          tags = Nil,
          period = None,
          summary = None,
          citedByCount = None,
          influentialCitations = None,
          provenance = None,
          calibreId = None,
          findings = List(f(0, 3, Some(Period(Year(-2550), Year(-2450)))), f(1, 4, None)),
          edges = Nil
        ),
        cfg.limits.chunkChars,
        cfg.places.pointToleranceKm
      )
      EventFixtures.put(
        ds,
        BEventRow.Id("zqtv_event"),
        EventPut(
          EventSummary.Label("Zqtv siege"),
          timeSpan = TimeSpan(notBefore = Some(Year(-1520)), notAfter = Some(Year(-1480))),
          placeId = Some(Place.Id("zqtv_evt"))
        )
      )
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private val Ours = "\"id\":\"(zqtv_[a-z]+)\"".r

  private def year(asOf: Option[Long]): Option[DeepTime.SignedYear] = asOf.map(DeepTime.SignedYear.apply)

  private def onMap(kind: String, asOf: Option[Long]): Set[String] =
    Ours
      .findAllMatchIn(PlaceFixtures.geoJson(ds, Some(KindWire(kind)), year(asOf), PlaceFixtures.Limit(100000)).value)
      .map(_.group(1))
      .toSet
  private def found(asOf: Option[Long]): Set[String] =
    PlaceFixtures.search(ds, Some(PlaceFixtures.SearchText("Zqtv")), year(asOf), PlaceFixtures.Limit(100)).map(_.id.value).toSet
  private def kids(asOf: Option[Long]): Set[String] =
    PlaceFixtures.children(ds, PlaceId("zqtv_root"), year(asOf), PlaceFixtures.Limit(100)).map(_.id.value).toSet
  private def tree(asOf: Option[Long]): Set[String] =
    PlaceFixtures.hierarchy(ds, year(asOf), Some(PlaceId("zqtv_root")), PlaceFixtures.Depth(3), PlaceFixtures.Limit(100)).map(_.id.value).toSet

  private val All: Set[String] = fixtures.map(_.place.id.value).toSet

  test("the fixtures' findings and event are where the cases need them"):
    assertEquals(PlaceFixtures.placesOf(ds, Finding.Id("zqtv_src#0")).map(_.id).toSet, Set("zqtv_evid"))
    assertEquals(PlaceFixtures.placesOf(ds, Finding.Id("zqtv_src#1")).map(_.id).toSet, Set("zqtv_undfind"))

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
      PlaceFixtures.search(ds, Some(PlaceFixtures.SearchText("Zqtv city")), Some(DeepTime.SignedYear(1499L)), PlaceFixtures.Limit(10), modernFrom = DeepTime.SignedYear(1400L)).map(_.id).toList,
      List("zqtv_city"),
      "the cutoff is the caller's (configured) value"
    )

  test("no time: nothing changes — every place is listed, the map across all time draws no present-day place"):
    assertEquals(found(None), All)
    assertEquals(kids(None), All - "zqtv_root")
    assertEquals(tree(None), All)
    assertEquals(onMap("site", None), All -- Set("zqtv_root", "zqtv_river", "zqtv_city"))

  test("the map feed says on what ground: findings for the undated site, dated for the rest"):
    val json = PlaceFixtures.geoJson(ds, Some(KindWire("site")), Some(DeepTime.SignedYear(-2500L)), PlaceFixtures.Limit(100000)).value
    def basisOf(id: String) = s"\"id\":\"$id\"[^}]*\"basis\":\"([a-z]+)\"".r.findFirstMatchIn(json).map(_.group(1))
    assertEquals(basisOf("zqtv_evid"), Some("findings"))
    assertEquals(basisOf("zqtv_dated"), Some("dated"))

  test("among points, dated places outrank the rest under the limit"):
    val json = PlaceFixtures.geoJson(ds, Some(KindWire("site")), Some(DeepTime.SignedYear(1600L)), PlaceFixtures.Limit(100000)).value
    val order = Ours.findAllMatchIn(json).map(_.group(1)).toList
    assertEquals(order, List("zqtv_su", "zqtv_city"))

  test("the SQL basis is the pure one, place by place; the card notes a place dated by its findings"):
    def attrs(id: String) = connect(ds)(
      sql"""SELECT kind, coalesce((properties->>'modern')::boolean, false), lower_inf(period) AND upper_inf(period),
                   geom IS NOT NULL FROM places WHERE id = $id""".query[(String, Boolean, Boolean, Boolean)].run().head
    )
    for id <- All do
      val (kind, modern, undated, geom) = attrs(id)
      val d = PlaceFixtures.get(ds, PlaceId.applyUnsafe(id), Some(DeepTime.SignedYear(-2500L))).get
      assertEquals(d.timeBasis, Place.TimeBasis.of(Place.TimeBasis.OfKind(kind), modern, imported = false, undated, geom), id)
    assert(PlaceFixtures.get(ds, PlaceId("zqtv_evid"), Some(DeepTime.SignedYear(-2500L))).get.datedByFindings)
    assert(!PlaceFixtures.get(ds, PlaceId("zqtv_evid"), None).get.datedByFindings)
    assertEquals(
      PlaceFixtures.get(ds, PlaceId("zqtv_root"), Some(DeepTime.SignedYear(-2500L))).get.children.map(_.id).toSet,
      Set("zqtv_dated", "zqtv_evid", "zqtv_river"),
      "the place's own children follow the rule too"
    )
