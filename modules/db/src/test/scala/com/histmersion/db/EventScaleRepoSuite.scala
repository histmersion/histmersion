package com.histmersion.db

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.values.PlaceKind
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.database.Migrate

/** An event's scale (V078, #1440) and the map's events feed against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped
  * otherwise): the column and its check, the scale derived from the kind, the sub-event rule, the place's kind and the
  * event's own geometry, a stated scale and its `auto` reset, and `EventFixtures.geo` — each event's anchor (a point,
  * or the point-on-surface of an area), the outline of a REGIONAL event's area only, the unplaced count — and the
  * bundle carrying a stated scale. Fixtures sit at 120–125°E, 50°S, ids start with `zqes`.
  */
class EventScaleRepoSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private val Lat = -50.0
  private def pt(lon: Double) = Some(s"""{"type":"Point","coordinates":[$lon,$Lat]}""")
  // a 2°×2° square west of the sites; its point-on-surface is inside it
  private val Square = Some(
    """{"type":"Polygon","coordinates":[[[120,-51],[122,-51],[122,-49],[120,-49],[120,-51]]]}"""
  )

  private val places = List(
    PlaceInput(Place("zqes_site", "Zqes site", PlaceKind.Site, pt(124.0)), List(PlaceName("Zqes site"))),
    PlaceInput(Place("zqes_area", "Zqes area", PlaceKind.Region, Square), List(PlaceName("Zqes area"))),
    PlaceInput(
      Place("zqes_ptregion", "Zqes point region", PlaceKind.Region, pt(125.0)),
      List(PlaceName("Zqes point region"))
    )
  )

  private def ev(
      id: String,
      kind: String,
      place: Option[String] = None,
      parent: Option[String] = None,
      geometry: Option[String] = None,
      scale: Option[String] = None
  ) =
    EventFixtures.put(
      ds,
      id,
      EventPut(
        s"Zqes $id",
        kind,
        TimeSpan(Some(-1300), None, None, Some(-1250)),
        geometry = geometry,
        placeId = place,
        parentId = parent,
        scale = scale
      )
    )

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM events WHERE id LIKE 'zqes%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zqes%'".update.run()

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      val _ = PlaceFixtures.upsertPlaces(ds, places)
      ev("zqes_battle_area", "battle", place = Some("zqes_area")) // kind: local, at an area
      ev("zqes_migration", "migration", place = Some("zqes_area")) // kind: regional, with an outline
      ev("zqes_event_site", "event", place = Some("zqes_site")) // place kind: local
      ev("zqes_event_area", "event", place = Some("zqes_area")) // place kind: regional
      ev("zqes_drought", "natural_disaster", place = Some("zqes_ptregion")) // regional, point only: no outline
      ev("zqes_sub", "event", parent = Some("zqes_migration")) // a sub-event, nothing else: local
      ev("zqes_sub_area", "event", place = Some("zqes_area"), parent = Some("zqes_migration")) // the area decides
      ev("zqes_own_point", "event", geometry = pt(123.5)) // its own point: local
      ev("zqes_own_area", "event", geometry = Square) // its own area: regional
      ev("zqes_unplaced", "battle") // nowhere
      ev("zqes_stated", "battle", place = Some("zqes_area"), scale = Some("regional"))
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def summary(id: String): EventSummary = EventFixtures.get(ds, id).getOrElse(fail(s"no $id")).event

  private def geo(): EventGeoList =
    val all = EventFixtures.geo(ds, Filter(), PlaceScope.empty, Some("Zqes"), None, None, None, 1000, 0.01)
    all.copy(events = all.events.filter(_.id.startsWith("zqes")))

  test("V078: the scale column takes local, regional or nothing — and nothing else"):
    val cols = connect(ds)(sql"""SELECT data_type FROM information_schema.columns
                                 WHERE table_name = 'events' AND column_name = 'scale'""".query[String].run())
    assertEquals(cols, Vector("text"))
    val refused =
      intercept[Exception](connect(ds)(sql"UPDATE events SET scale = 'global' WHERE id = 'zqes_unplaced'".update.run()))
    assert(refused.getMessage.contains("events_scale_check"), refused.getMessage)
    // nothing was backfilled: the derived ones stay NULL, so a changed kind still decides
    val stored =
      connect(ds)(sql"SELECT id, scale FROM events WHERE id LIKE 'zqes%'".query[(String, Option[String])].run().toMap)
    assertEquals(stored.collect { case (id, Some(s)) => id -> s }, Map("zqes_stated" -> "regional"))

  test("a scale not stated is derived: kind, then own geometry, then the place's kind, then sub-event"):
    val got = List(
      "zqes_battle_area",
      "zqes_migration",
      "zqes_event_site",
      "zqes_event_area",
      "zqes_drought",
      "zqes_sub",
      "zqes_sub_area",
      "zqes_own_point",
      "zqes_own_area",
      "zqes_unplaced"
    ).map(id => id -> summary(id).scale).toMap
    assertEquals(
      got,
      Map(
        "zqes_battle_area" -> "local",
        "zqes_migration" -> "regional",
        "zqes_event_site" -> "local",
        "zqes_event_area" -> "regional",
        "zqes_drought" -> "regional",
        "zqes_sub" -> "local",
        "zqes_sub_area" -> "regional",
        "zqes_own_point" -> "local",
        "zqes_own_area" -> "regional",
        "zqes_unplaced" -> "local"
      )
    )
    assert(!summary("zqes_migration").scaleStated)
    // the list carries the same
    val listed = EventFixtures
      .list(ds, Filter(), PlaceScope.empty, Some("Zqes"), None, None, None, 100, 0)
      .events
      .map(e => e.id -> e.scale)
      .toMap
    assertEquals(listed("zqes_event_area"), "regional")
    assertEquals(listed("zqes_stated"), "regional")

  test("a stated scale wins; a patch states one, and `auto` forgets it"):
    assertEquals((summary("zqes_stated").scale, summary("zqes_stated").scaleStated), ("regional", true))
    assertEquals(EventFixtures.patch(ds, "zqes_stated", EventPatch(scale = Some("auto"))), Right(()))
    assertEquals((summary("zqes_stated").scale, summary("zqes_stated").scaleStated), ("local", false))
    assertEquals(EventFixtures.patch(ds, "zqes_stated", EventPatch(scale = Some("Regional"))), Right(()))
    assertEquals((summary("zqes_stated").scale, summary("zqes_stated").scaleStated), ("regional", true))
    // a patch of something else keeps it
    assertEquals(EventFixtures.patch(ds, "zqes_stated", EventPatch(description = Some("d"))), Right(()))
    assertEquals(summary("zqes_stated").scale, "regional")

  test("the map feed: the anchor, the outline of a regional area only, the unplaced count"):
    val g = geo()
    val by = g.events.map(e => e.id -> e).toMap
    assert(!by.contains("zqes_unplaced"))
    assert(g.unplaced >= 1, g.toString)
    // a site's point, the event's own point
    assertEquals((by("zqes_event_site").lon, by("zqes_event_site").lat), (124.0, Lat))
    assertEquals(by("zqes_event_site").anchor, "place")
    assertEquals(by("zqes_own_point").anchor, "event")
    assertEquals((by("zqes_own_point").lon, by("zqes_own_point").lat), (123.5, Lat))
    // an area's point-on-surface lies inside the square
    List("zqes_battle_area", "zqes_migration", "zqes_own_area").foreach { id =>
      val e = by(id)
      assert(e.lon > 120 && e.lon < 122 && e.lat > -51 && e.lat < -49, s"$id at ${e.lon},${e.lat}")
    }
    // the outline: regional with an area — not a local event at the same area, not a point-only region
    assert(by("zqes_migration").outline.exists(_.contains("Polygon")), by("zqes_migration").toString)
    assert(by("zqes_own_area").outline.isDefined)
    assert(by("zqes_event_area").outline.isDefined)
    assertEquals(by("zqes_battle_area").outline, None)
    assert(!by.contains("zqes_sub"), "a sub-event with no place is not on the map")
    assert(by("zqes_sub_area").outline.isDefined)
    assertEquals(by("zqes_drought").outline, None)
    assertEquals((by("zqes_drought").lon, by("zqes_drought").scale), (125.0, "regional"))
    assertEquals(by("zqes_drought").placeLabel, Some("Zqes point region"))

  test("the feed is fast enough to start a playback: 50 runs well under the frame budget"):
    val t0 = System.nanoTime()
    (1 to 50).foreach(_ => geo())
    val perCall = (System.nanoTime() - t0) / 50 / 1000000
    assert(perCall < 150, s"$perCall ms per /api/events/geo")

  test("a bundle carries a stated scale, and leaves a derived one out"):
    val events =
      connect(ds)(
        com.histmersion.util.data.exchange.TestExchange.kinds
          .entities(BundleScope.All, com.histmersion.util.storage.ContentStore.none)
          .events
      ).filter(_.id.startsWith("zqes")).map(e => e.id -> e.scale).toMap
    assertEquals(events("zqes_stated"), Some("regional"))
    assertEquals(events("zqes_migration"), None)
