package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*

/** V089 (#1768) against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise): the generated
  * `events.outer_span` is core.EventGeo.outer — an open-ended event covers the end it states, not every
  * year after it — and the map's feed (`/api/events/geo`) and the list follow it. Ids start `zqos`. */
class EventOuterSpanSuite extends com.histmersion.db.DbSuite:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM events WHERE id LIKE 'zqos%'".update.run()

  override def beforeAll(): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def put(id: String, ts: TimeSpan, kind: String = "war") =
    EventRepo.put(ds, id, EventPut(s"Zqos $id", kind, ts, geometry = Some("""{"type":"Point","coordinates":[163.0,-46.0]}""")))

  /** The stored span as (lower, inclusive upper), `None` for an unbounded side. */
  private def span(id: String): (Option[Long], Option[Long]) =
    connect(ds)(sql"""SELECT CASE WHEN lower_inf(outer_span) THEN NULL ELSE lower(outer_span) END,
                             CASE WHEN upper_inf(outer_span) THEN NULL ELSE upper(outer_span) - 1 END
                      FROM events WHERE id = $id""".query[(Option[Long], Option[Long])].run().head)

  test("V089: the generated outer_span is core.EventGeo.outer (an undated event stays unbounded)"):
    val spans = List(
      TimeSpan(Some(-1300), Some(-1290), Some(-1260), Some(-1250)),
      TimeSpan(beginBy = Some(-1290), endFrom = Some(-1260)),
      TimeSpan(notBefore = Some(-1274)),
      TimeSpan(beginBy = Some(-1200)),
      TimeSpan(notBefore = Some(-1200), beginBy = Some(-1195)),
      TimeSpan(notAfter = Some(-1100)),
      TimeSpan(endFrom = Some(-1110), notAfter = Some(-1100)),
      TimeSpan(Some(-10), None, Some(5), Some(30)),
      TimeSpan())
    spans.zipWithIndex.foreach { (ts, i) =>
      put(s"zqos_s$i", ts)
      val expected = EventGeo.outer(ts).fold((None, None))((a, b) => (Some(a), Some(b)))
      assertEquals(span(s"zqos_s$i"), expected, ts.toString)
    }

  test("an event with only a start is in the views of its start, not of every later period"):
    put("zqos_case_open", TimeSpan(notBefore = Some(-1200)))
    put("zqos_case_closed", TimeSpan(Some(-1210), None, None, Some(-1190)))
    def geoIds(p: Period) =
      EventRepo.geo(ds, Filter(period = Some(p)), PlaceScope.empty, Some("Zqos zqos_case_"), None, None, None, 100, 0.01)
        .events.map(_.id).toSet
    def listIds(p: Period) =
      EventRepo.list(ds, Filter(period = Some(p)), PlaceScope.empty, Some("Zqos zqos_case_"), None, None, None, 100, 0)
        .events.map(_.id).toSet
    assertEquals(geoIds(Period(-1205, -1195)), Set("zqos_case_open", "zqos_case_closed"))
    assertEquals(geoIds(Period(-500, -400)), Set.empty[String], "not in a view of a later period")
    assertEquals(listIds(Period(-500, -400)), Set.empty[String], "nor in the list of one")
