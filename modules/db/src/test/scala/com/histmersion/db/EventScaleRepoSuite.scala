package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.TimeSpan
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.data.exchange.BundleScope
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{EventPatch, EventPut, EventSummary, Place}
import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.EventGeo
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Event
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.catalogue.events.EventSelection
import com.histmersion.domain.catalogue.events.BEventRow
import com.histmersion.util.data.exchange.TestExchange
import com.histmersion.util.storage.ContentStore
import com.histmersion.lint.{tolerated, LintRule}

/** An event's scale (V078, #1440) and the map's events feed against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped
  * otherwise): the column and its check, the scale derived from the kind, the sub-event rule, the place's kind and the
  * event's own geometry, a stated scale and its `auto` reset, and `EventFixtures.geo` — each event's anchor (a point,
  * or the point-on-surface of an area), the outline of a REGIONAL event's area only, the unplaced count — and the
  * bundle carrying a stated scale. Fixtures sit at 120–125°E, 50°S, ids start with `zqes`.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class EventScaleRepoSuite extends DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private val Lat = -50.0
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pt(lon: Longitude) = Some(s"""{"type":"Point","coordinates":[${lon.value},$Lat]}""")
  // a 2°×2° square west of the sites; its point-on-surface is inside it
  private val Square = Some(
    """{"type":"Polygon","coordinates":[[[120,-51],[122,-51],[122,-49],[120,-49],[120,-51]]]}"""
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val places = List(
    Place.Input(
      Place(
        Place.Id("zqes_site"),
        Place.Label("Zqes site"),
        Place.KindValue(domain.catalogue.Place.Kind.Site),
        pt(Longitude(124.0)).map(Place.Geometry(_))
      ),
      List(Place.Name(Place.Name.Value("Zqes site")))
    ),
    Place.Input(
      Place(
        Place.Id("zqes_area"),
        Place.Label("Zqes area"),
        Place.KindValue(domain.catalogue.Place.Kind.Region),
        Square.map(Place.Geometry(_))
      ),
      List(Place.Name(Place.Name.Value("Zqes area")))
    ),
    Place.Input(
      Place(
        Place.Id("zqes_ptregion"),
        Place.Label("Zqes point region"),
        Place.KindValue(domain.catalogue.Place.Kind.Region),
        pt(Longitude(125.0)).map(Place.Geometry(_))
      ),
      List(Place.Name(Place.Name.Value("Zqes point region")))
    )
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def ev(
      id: BEventRow.Id,
      kind: EventSummary.Kind,
      place: Option[Place.Id] = None,
      parent: Option[Event.Id] = None,
      geometry: Option[EventPut.Geometry] = None,
      scale: Option[EventSummary.Scale] = None
  ) =
    EventFixtures.put(
      ds,
      id,
      EventPut(
        EventSummary.Label(s"Zqes ${id.value}"),
        kind,
        TimeSpan(Some(Year(-1300)), None, None, Some(Year(-1250))),
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
      ev(
        BEventRow.Id("zqes_battle_area"),
        EventSummary.Kind("battle"),
        place = Some(Place.Id("zqes_area"))
      ) // kind: local, at an area
      ev(
        BEventRow.Id("zqes_migration"),
        EventSummary.Kind("migration"),
        place = Some(Place.Id("zqes_area"))
      ) // kind: regional, with an outline
      ev(
        BEventRow.Id("zqes_event_site"),
        EventSummary.Kind("event"),
        place = Some(Place.Id("zqes_site"))
      ) // place kind: local
      ev(
        BEventRow.Id("zqes_event_area"),
        EventSummary.Kind("event"),
        place = Some(Place.Id("zqes_area"))
      ) // place kind: regional
      ev(
        BEventRow.Id("zqes_drought"),
        EventSummary.Kind("natural_disaster"),
        place = Some(Place.Id("zqes_ptregion"))
      ) // regional, point only: no outline
      ev(
        BEventRow.Id("zqes_sub"),
        EventSummary.Kind("event"),
        parent = Some(Event.Id("zqes_migration"))
      ) // a sub-event, nothing else: local
      ev(
        BEventRow.Id("zqes_sub_area"),
        EventSummary.Kind("event"),
        place = Some(Place.Id("zqes_area")),
        parent = Some(Event.Id("zqes_migration"))
      ) // the area decides
      ev(
        BEventRow.Id("zqes_own_point"),
        EventSummary.Kind("event"),
        geometry = pt(Longitude(123.5)).map(EventPut.Geometry(_))
      ) // its own point: local
      ev(
        BEventRow.Id("zqes_own_area"),
        EventSummary.Kind("event"),
        geometry = Square.map(EventPut.Geometry(_))
      ) // its own area: regional
      ev(BEventRow.Id("zqes_unplaced"), EventSummary.Kind("battle")) // nowhere
      ev(
        BEventRow.Id("zqes_stated"),
        EventSummary.Kind("battle"),
        place = Some(Place.Id("zqes_area")),
        scale = Some(EventSummary.Scale("regional"))
      )
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def summary(id: BEventRow.Id): EventSummary =
    EventFixtures.get(ds, id).getOrElse(fail(s"no ${id.value}")).event

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def geo(): EventGeo.List =
    val all = EventFixtures.geo(
      ds,
      Filter(),
      PlaceScope.empty,
      Some(EventSelection.Text("Zqes")),
      None,
      None,
      None,
      PageSize(1000),
      EventFixtures.GeoTolerance(0.01)
    )
    all.modify(_.events).setTo(all.events.filter(_.id.value.startsWith("zqes")))

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
    ).map(id => id -> summary(BEventRow.Id(id)).scale).toMap
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
    assert(!summary(BEventRow.Id("zqes_migration")).scaleStated.value)
    // the list carries the same
    val listed = EventFixtures
      .list(
        ds,
        Filter(),
        PlaceScope.empty,
        Some(EventSelection.Text("Zqes")),
        None,
        None,
        None,
        PageSize(100),
        Offset(0)
      )
      .events
      .map(e => e.id -> e.scale)
      .toMap
    assertEquals(listed(Event.Id("zqes_event_area")), "regional")
    assertEquals(listed(Event.Id("zqes_stated")), "regional")

  test("a stated scale wins; a patch states one, and `auto` forgets it"):
    assertEquals(
      (summary(BEventRow.Id("zqes_stated")).scale, summary(BEventRow.Id("zqes_stated")).scaleStated),
      ("regional", true)
    )
    assertEquals(
      EventFixtures.patch(ds, BEventRow.Id("zqes_stated"), EventPatch(scale = Some(EventSummary.Scale("auto")))),
      Right(())
    )
    assertEquals(
      (summary(BEventRow.Id("zqes_stated")).scale, summary(BEventRow.Id("zqes_stated")).scaleStated),
      ("local", false)
    )
    assertEquals(
      EventFixtures.patch(ds, BEventRow.Id("zqes_stated"), EventPatch(scale = Some(EventSummary.Scale("Regional")))),
      Right(())
    )
    assertEquals(
      (summary(BEventRow.Id("zqes_stated")).scale, summary(BEventRow.Id("zqes_stated")).scaleStated),
      ("regional", true)
    )
    // a patch of something else keeps it
    assertEquals(
      EventFixtures
        .patch(ds, BEventRow.Id("zqes_stated"), EventPatch(description = Some(EventSummary.Description("d")))),
      Right(())
    )
    assertEquals(summary(BEventRow.Id("zqes_stated")).scale, "regional")

  test("the map feed: the anchor, the outline of a regional area only, the unplaced count"):
    val g = geo()
    val by = g.events.map(e => e.id -> e).toMap
    assert(!by.contains(Event.Id("zqes_unplaced")))
    assert(g.unplaced.value >= 1, g.toString)
    // a site's point, the event's own point
    assertEquals((by(Event.Id("zqes_event_site")).lon, by(Event.Id("zqes_event_site")).lat), (124.0, Lat))
    assertEquals(by(Event.Id("zqes_event_site")).anchor, "place")
    assertEquals(by(Event.Id("zqes_own_point")).anchor, "event")
    assertEquals((by(Event.Id("zqes_own_point")).lon, by(Event.Id("zqes_own_point")).lat), (123.5, Lat))
    // an area's point-on-surface lies inside the square
    List("zqes_battle_area", "zqes_migration", "zqes_own_area").foreach { id =>
      val e = by(Event.Id(id))
      assert(
        e.lon.value > 120 && e.lon.value < 122 && e.lat.value > -51 && e.lat.value < -49,
        s"$id at ${e.lon},${e.lat}"
      )
    }
    // the outline: regional with an area — not a local event at the same area, not a point-only region
    assert(
      by(Event.Id("zqes_migration")).outline.exists(_.value.contains("Polygon")),
      by(Event.Id("zqes_migration")).toString
    )
    assert(by(Event.Id("zqes_own_area")).outline.isDefined)
    assert(by(Event.Id("zqes_event_area")).outline.isDefined)
    assertEquals(by(Event.Id("zqes_battle_area")).outline, None)
    assert(!by.contains(Event.Id("zqes_sub")), "a sub-event with no place is not on the map")
    assert(by(Event.Id("zqes_sub_area")).outline.isDefined)
    assertEquals(by(Event.Id("zqes_drought")).outline, None)
    assertEquals((by(Event.Id("zqes_drought")).lon, by(Event.Id("zqes_drought")).scale), (125.0, "regional"))
    assertEquals(by(Event.Id("zqes_drought")).placeLabel, Some("Zqes point region"))

  test("the feed is fast enough to start a playback: 50 runs well under the frame budget"):
    val t0 = System.nanoTime()
    (1 to 50).foreach(_ => geo())
    val perCall = (System.nanoTime() - t0) / 50 / 1000000
    assert(perCall < 150, s"$perCall ms per /api/events/geo")

  test("a bundle carries a stated scale, and leaves a derived one out"):
    val events =
      connect(ds)(
        TestExchange.kinds
          .entities(BundleScope.All, ContentStore.none)
          .events
      ).filter(_.id.value.startsWith("zqes")).map(e => e.id -> e.scale).toMap
    assertEquals(events(Event.Id("zqes_stated")), Some("regional"))
    assertEquals(events(Event.Id("zqes_migration")), None)
