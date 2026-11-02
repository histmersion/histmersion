package com.histmersion.domain.catalogue.events

import com.histmersion.util.database.given

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.TimeSpan
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import scala.util.Random
import com.histmersion.domain.catalogue.Historicity
import com.histmersion.util.database.Migrate
import com.histmersion.db.*
import com.histmersion.api.catalogue.{
  CatalogRecord,
  Corrections,
  EventFields,
  EventPatch,
  EventPrecision,
  EventPut,
  Period
}
import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.Correction

/** The data-source assessment's P0 fixes against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise): V088's
  * generated uncertainty agrees with core.EventPrecision; the map's feed leaves the imprecise events out (and counts
  * them) while the list keeps them; the merge against stored rows does not depend on their order; corrections by hand
  * survive a re-import — kept while the catalogue is unchanged, retired once it agrees, flagged when it changes to
  * something else — follow a rename, go with a delete, and a conflict is a lint item. Ids start with `zqcr` (provider
  * `zzcr`).
  */
class EventCorrectionsRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private val P = "zzcr"

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM corrections WHERE target_id LIKE 'zqcr%' OR target_id LIKE 'zzcr%'".update.run()
    val _ = sql"DELETE FROM events WHERE id LIKE 'zqcr%' OR id LIKE 'zzcr%'".update.run()
    val _ = sql"DELETE FROM event_aliases WHERE alias LIKE 'zzcr%'".update.run()

  override def beforeAll(): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def put(id: String, ts: TimeSpan, lon: Double = 160.0) =
    EventFixtures.put(
      ds,
      id,
      EventPut(s"Zqcr $id", "eruption", ts, geometry = Some(s"""{"type":"Point","coordinates":[$lon,-45.0]}"""))
    )

  test("V088: the generated uncertainty is core.EventPrecision.uncertainty"):
    val spans = List(
      TimeSpan(Some(79), Some(79), Some(79), Some(79)) -> None,
      TimeSpan(Some(-2251), None, None, Some(2026)) -> Some(2500L),
      TimeSpan(Some(-2251), None, None, Some(2026)) -> None,
      TimeSpan(Some(-1000), None, None, Some(1)) -> None,
      TimeSpan(Some(-1300), Some(-1100), Some(-1000), Some(-990)) -> None,
      TimeSpan(Some(-431), Some(-431), Some(-404), Some(-404)) -> Some(3L),
      TimeSpan(None, Some(-50), None, None) -> None,
      TimeSpan(Some(-10), None, Some(5), Some(30)) -> None,
      TimeSpan() -> None
    )
    spans.zipWithIndex.foreach { case ((ts, pm), i) =>
      put(s"zqcr_u$i", ts)
      val _ = connect(ds)(sql"UPDATE events SET plus_minus = $pm WHERE id = ${s"zqcr_u$i"}".update.run())
      val got =
        connect(ds)(sql"SELECT uncertainty FROM events WHERE id = ${s"zqcr_u$i"}".query[Option[Long]].run().head)
      assertEquals(got, EventPrecision.uncertainty(ts, pm), s"$ts ± $pm")
    }

  test("the map's feed leaves the imprecise out and counts them; the list keeps them, with the class"):
    put("zqcr_precise", TimeSpan(Some(-1610), Some(-1610), Some(-1610), Some(-1610)))
    put("zqcr_approx", TimeSpan(Some(-1800), None, None, Some(-1400)))
    put("zqcr_imprecise", TimeSpan(Some(-4000), None, None, Some(1000)))
    val f = Filter(period = Some(Period(-1700, -1500)))
    val geo = EventFixtures.geo(ds, f, PlaceScope.empty, Some("Zqcr zqcr_"), None, None, None, 100, 0.01)
    assertEquals(
      geo.events.map(g => g.id -> g.precision).toSet,
      Set("zqcr_precise" -> EventPrecision.Precise, "zqcr_approx" -> EventPrecision.Approximate)
    )
    assert(geo.imprecise >= 1, geo.imprecise.toString)
    val listed = EventFixtures
      .list(ds, f, PlaceScope.empty, Some("Zqcr zqcr_"), None, None, None, 100, 0)
      .events
      .map(e => e.id -> e.precision)
      .toMap
    assertEquals(listed.get("zqcr_imprecise"), Some(EventPrecision.Imprecise), "listed, not drawn")

  test("the merge reads stored rows in id order and places shuffled inputs identically"):
    val ids = Random(7).shuffle((1 to 6).map(i => s"zqcr_m$i").toList)
    ids.foreach(id => put(id, TimeSpan(Some(-900 - id.last.asDigit), None, None, Some(-890 + id.last.asDigit)), 161.0))
    val stored = EventFixtures.stored(ds)
    assertEquals(stored.map(_.id), stored.map(_.id).sorted)
    val mine = stored.filter(_.id.startsWith("zqcr_m"))
    val recs = (1 to 4)
      .map(i =>
        CatalogRecord(
          P,
          s"r$i",
          "",
          s"r$i",
          "eruption",
          TimeSpan(Some(-900L + i), Some(-900L + i), Some(-900L + i), Some(-900L + i)),
          Some(-45.0),
          Some(161.01)
        )
      )
      .toList
    val tol = CatalogMerge.Tolerance(60, 30)
    val expected = CatalogMerge.place(recs, mine, tol).map((r, p) => r.key -> p.eventId).toMap
    assertEquals(
      expected.values.toSet,
      Set("zqcr_m1"),
      "the narrowest overlapping event (its span centred nearest) wins for all"
    )
    val rnd = Random(88)
    (1 to 10).foreach { _ =>
      assertEquals(
        CatalogMerge.place(rnd.shuffle(recs), rnd.shuffle(mine), tol).map((r, p) => r.key -> p.eventId).toMap,
        expected
      )
    }

  // ── corrections ────────────────────────────────────────────────────────────────────────────

  private val upstream0 = CatalogRecord(
    P,
    "c1",
    "https://example.org/c1",
    "Eruption of Zqcr",
    "eruption",
    TimeSpan(Some(-2251), None, None, Some(2026)),
    Some(-45.0),
    Some(162.0),
    description = Some("Confirmed Eruption at Zqcr"),
    central = Some(250),
    plusMinus = Some(2500),
    datingMethod = Some("Tephrochronology")
  )
  private val Id = "zzcr_c1"

  private def reimport(r: CatalogRecord): EventSaved =
    EventFixtures.save(ds, CatalogMerge.place(List(r), EventFixtures.stored(ds), CatalogMerge.Tolerance(60, 30)))
  private def event = EventFixtures.get(ds, Id).get
  private def corrections =
    EventFixtures.corrections(ds, Correction.Target.Event, Some(Id), Some("active"), 50, 0).corrections ++
      EventFixtures.corrections(ds, Correction.Target.Event, Some(Id), Some("conflict"), 50, 0).corrections ++
      EventFixtures.corrections(ds, Correction.Target.Event, Some(Id), Some("agreed"), 50, 0).corrections

  test("corrections: a patch on a catalogue event is kept by a re-import while the catalogue is unchanged"):
    val first = reimport(upstream0)
    assertEquals(first.created, 1)
    val e0 = event.event
    assertEquals(
      (e0.central, e0.plusMinus, e0.datingMethod, e0.precision),
      (Some(250L), Some(2500L), Some("Tephrochronology"), EventPrecision.Imprecise)
    )
    assertEquals(e0.datingNote, Some("250 CE ± 2500 (tephrochronology)"))
    assert(
      EventFixtures
        .patch(
          ds,
          Id,
          EventPatch(
            label = Some("Hekla 3 (Zqcr)"),
            description = Some("my note"),
            historicity = Some(Historicity.Attested)
          )
        )
        .isRight
    )
    // historicity equals the catalogue's: no correction for it
    assertEquals(
      corrections.map(c => c.field -> c.status).toSet,
      Set(EventFields.Label -> Correction.Status.Active, EventFields.Description -> Correction.Status.Active)
    )
    assertEquals(
      corrections.find(_.field == EventFields.Label).map(c => Corrections.show(c.upstreamValue)),
      Some("Eruption of Zqcr")
    )
    val again = reimport(upstream0)
    assertEquals((again.created, again.updated, again.corrections), (0, 1, Correction.Counts(kept = 2)))
    assertEquals(
      (event.event.label, event.event.description),
      ("Hekla 3 (Zqcr)", Some("my note")),
      "kept over the catalogue's"
    )
    assertEquals(event.corrections.size, 2, "the card lists them")

  test("corrections: retired once the catalogue agrees; flagged when it changes to something else"):
    val agrees = reimport(upstream0.copy(label = "Hekla 3 (Zqcr)"))
    assertEquals(agrees.corrections, Correction.Counts(kept = 1, retired = 1))
    assertEquals(corrections.find(_.field == EventFields.Label).map(_.status), Some(Correction.Status.Agreed))
    assertEquals(event.event.label, "Hekla 3 (Zqcr)")
    // the catalogue now says something else about the description: the person's stays, flagged
    val changed =
      upstream0.copy(label = "Hekla 3 (Zqcr)", description = Some("Confirmed Eruption at Zqcr; area: SW flank"))
    val c = reimport(changed)
    assertEquals(c.corrections, Correction.Counts(conflicts = 1))
    assertEquals(event.event.description, Some("my note"))
    val conflict = EventFixtures
      .corrections(ds, Correction.Target.Event, None, Some("conflict"), 50, 0)
      .corrections
      .filter(_.targetId == Id)
    assertEquals(
      conflict.map(x => (x.field, x.upstreamNow.map(Corrections.show))),
      List(EventFields.Description -> Some("Confirmed Eruption at Zqcr; area: SW flank"))
    )
    assertEquals(conflict.head.label, Some("Hekla 3 (Zqcr)"))
    val lint = connect(ds)(CorrectionRepo.conflictsIn())
    assert(lint.exists((id, detail) => id == Id && detail.startsWith("description:")), lint.toString)
    // still a conflict on the next import (the catalogue unchanged since), and back to active if it reverts
    assertEquals(reimport(changed).corrections, Correction.Counts(conflicts = 1))
    assertEquals(reimport(upstream0.copy(label = "Hekla 3 (Zqcr)")).corrections, Correction.Counts(kept = 1))
    // setting the field to the catalogue's value takes the catalogue's: the correction goes
    assert(EventFixtures.patch(ds, Id, EventPatch(description = Some("Confirmed Eruption at Zqcr"))).isRight)
    assertEquals(corrections.filter(_.field == EventFields.Description), Nil)

  test("corrections: a span set by hand drops the stated year and ±, and survives; a rename and a delete carry them"):
    val mine = TimeSpan(Some(1104), Some(1104), Some(1104), Some(1104))
    assert(EventFixtures.patch(ds, Id, EventPatch(timeSpan = Some(mine))).isRight)
    assertEquals(
      (event.event.central, event.event.plusMinus, event.event.precision),
      (None, None, EventPrecision.Precise)
    )
    val _ = reimport(upstream0.copy(label = "Hekla 3 (Zqcr)"))
    assertEquals(
      (event.event.timeSpan, event.event.central),
      (mine, None),
      "the person's span, not the catalogue's ± window"
    )
    // an event compiled from sources records nothing: no import rewrites it
    put("zqcr_compiled", TimeSpan(Some(1), Some(1), Some(1), Some(1)))
    assert(EventFixtures.patch(ds, "zqcr_compiled", EventPatch(label = Some("x"))).isRight)
    assertEquals(EventFixtures.corrections(ds, Correction.Target.Event, Some("zqcr_compiled"), None, 10, 0).total, 0)
    // rename: the corrections follow the event
    assert(EventFixtures.rename(ds, Id, "zzcr_renamed").isRight)
    assertEquals(
      EventFixtures
        .corrections(ds, Correction.Target.Event, Some("zzcr_renamed"), None, 10, 0)
        .corrections
        .map(_.field),
      List(EventFields.Span)
    )
    // the renamed event is still refreshed from its owner record, the correction applied
    val _ = reimport(upstream0)
    assertEquals(EventFixtures.get(ds, "zzcr_renamed").map(_.event.timeSpan), Some(mine))
    assert(EventFixtures.delete(ds, "zzcr_renamed"))
    assertEquals(EventFixtures.corrections(ds, Correction.Target.Event, Some("zzcr_renamed"), None, 10, 0).total, 0)
