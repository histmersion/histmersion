package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.TimeSpan
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.Migrate
import com.histmersion.api.catalogue.{EventPut, EventSummary, Period}
import com.histmersion.api.library.Filter
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.catalogue.events.EventSelection

/** V089 (#1768) against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise): the generated `events.outer_span`
  * is TimeSpan.outer — an open-ended event covers the end it states, not every year after it — and the map's feed
  * (`/api/events/geo`) and the list follow it. Ids start `zqos`.
  */
class EventOuterSpanSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM events WHERE id LIKE 'zqos%'".update.run()

  override def beforeAll(): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def put(id: BEventRow.Id, ts: TimeSpan, kind: EventSummary.Kind = EventSummary.Kind("war")) =
    EventFixtures.put(
      ds,
      id,
      EventPut(
        EventSummary.Label(s"Zqos $id"),
        kind,
        ts,
        geometry = Some(EventPut.Geometry("""{"type":"Point","coordinates":[163.0,-46.0]}"""))
      )
    )

  /** The stored span as (lower, inclusive upper), `None` for an unbounded side. */
  private def span(id: String): (Option[Long], Option[Long]) =
    connect(ds)(sql"""SELECT CASE WHEN lower_inf(outer_span) THEN NULL ELSE lower(outer_span) END,
                             CASE WHEN upper_inf(outer_span) THEN NULL ELSE upper(outer_span) - 1 END
                      FROM events WHERE id = $id""".query[(Option[Long], Option[Long])].run().head)

  test("V089: the generated outer_span is TimeSpan.outer (an undated event stays unbounded)"):
    val spans = List(
      TimeSpan(Some(Year(-1300)), Some(Year(-1290)), Some(Year(-1260)), Some(Year(-1250))),
      TimeSpan(beginBy = Some(Year(-1290)), endFrom = Some(Year(-1260))),
      TimeSpan(notBefore = Some(Year(-1274))),
      TimeSpan(beginBy = Some(Year(-1200))),
      TimeSpan(notBefore = Some(Year(-1200)), beginBy = Some(Year(-1195))),
      TimeSpan(notAfter = Some(Year(-1100))),
      TimeSpan(endFrom = Some(Year(-1110)), notAfter = Some(Year(-1100))),
      TimeSpan(Some(Year(-10)), None, Some(Year(5)), Some(Year(30))),
      TimeSpan()
    )
    spans.zipWithIndex.foreach { (ts, i) =>
      put(BEventRow.Id(s"zqos_s$i"), ts)
      val expected = ts.outer.fold((None, None))((a, b) => (Some(a), Some(b)))
      assertEquals(span(s"zqos_s$i"), expected, ts.toString)
    }

  test("an event with only a start is in the views of its start, not of every later period"):
    put(BEventRow.Id("zqos_case_open"), TimeSpan(notBefore = Some(Year(-1200))))
    put(BEventRow.Id("zqos_case_closed"), TimeSpan(Some(Year(-1210)), None, None, Some(Year(-1190))))
    def geoIds(p: Period) =
      EventFixtures
        .geo(
          ds,
          Filter(period = Some(p)),
          PlaceScope.empty,
          Some(EventSelection.Text("Zqos zqos_case_")),
          None,
          None,
          None,
          PageSize(100),
          EventFixtures.GeoTolerance(0.01)
        )
        .events
        .map(_.id)
        .toSet
    def listIds(p: Period) =
      EventFixtures
        .list(
          ds,
          Filter(period = Some(p)),
          PlaceScope.empty,
          Some(EventSelection.Text("Zqos zqos_case_")),
          None,
          None,
          None,
          PageSize(100),
          Offset(0)
        )
        .events
        .map(_.id)
        .toSet
    assertEquals(geoIds(Period(Year(-1205), Year(-1195))), Set("zqos_case_open", "zqos_case_closed"))
    assertEquals(geoIds(Period(Year(-500), Year(-400))), Set.empty[String], "not in a view of a later period")
    assertEquals(listIds(Period(Year(-500), Year(-400))), Set.empty[String], "nor in the list of one")
