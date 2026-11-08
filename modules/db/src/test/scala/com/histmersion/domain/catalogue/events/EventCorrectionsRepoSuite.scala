package com.histmersion
package domain.catalogue.events

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

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
import com.histmersion.db.DbSuite
import com.histmersion.config.DbConfig
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Event
import com.histmersion.api.catalogue.Latitude

/** The data-source assessment's P0 fixes against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise): V088's
  * generated uncertainty agrees with core.EventPrecision; the map's feed leaves the imprecise events out (and counts
  * them) while the list keeps them; the merge against stored rows does not depend on their order; corrections by hand
  * survive a re-import — kept while the catalogue is unchanged, retired once it agrees, flagged when it changes to
  * something else — follow a rename, go with a delete, and a conflict is a lint item. Ids start with `zqcr` (provider
  * `zzcr`).
  */
class EventCorrectionsRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val ds =
    val d = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private val P = CatalogRecord.Provider("zzcr")

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM corrections WHERE target_id LIKE 'zqcr%' OR target_id LIKE 'zzcr%'".update.run()
    val _ = sql"DELETE FROM events WHERE id LIKE 'zqcr%' OR id LIKE 'zzcr%'".update.run()
    val _ = sql"DELETE FROM event_aliases WHERE alias LIKE 'zzcr%'".update.run()

  override def beforeAll(): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def put(id: BEventRow.Id, ts: TimeSpan, lon: Longitude = Longitude(160.0)) =
    EventFixtures.put(
      ds,
      id,
      EventPut(
        EventSummary.Label(s"Zqcr ${id.value}"),
        EventSummary.Kind("eruption"),
        ts,
        geometry = Some(EventPatch.Geometry(s"""{"type":"Point","coordinates":[${lon.value},-45.0]}"""))
      )
    )

  test("V088: the generated uncertainty is core.EventPrecision.uncertainty"):
    val spans = List(
      TimeSpan(Some(Year(79)), Some(Year(79)), Some(Year(79)), Some(Year(79))) -> None,
      TimeSpan(Some(Year(-2251)), None, None, Some(Year(2026))) -> Some(2500L),
      TimeSpan(Some(Year(-2251)), None, None, Some(Year(2026))) -> None,
      TimeSpan(Some(Year(-1000)), None, None, Some(Year(1))) -> None,
      TimeSpan(Some(Year(-1300)), Some(Year(-1100)), Some(Year(-1000)), Some(Year(-990))) -> None,
      TimeSpan(Some(Year(-431)), Some(Year(-431)), Some(Year(-404)), Some(Year(-404))) -> Some(3L),
      TimeSpan(None, Some(Year(-50)), None, None) -> None,
      TimeSpan(Some(Year(-10)), None, Some(Year(5)), Some(Year(30))) -> None,
      TimeSpan() -> None
    )
    spans.zipWithIndex.foreach { case ((ts, pm), i) =>
      put(BEventRow.Id(s"zqcr_u$i"), ts)
      val _ = connect(ds)(sql"UPDATE events SET plus_minus = $pm WHERE id = ${s"zqcr_u$i"}".update.run())
      val got =
        connect(ds)(sql"SELECT uncertainty FROM events WHERE id = ${s"zqcr_u$i"}".query[Option[Long]].run().head)
      assertEquals(got, EventPrecision.uncertainty(ts, pm.map(EventSummary.PlusMinus(_))), s"$ts ± $pm")
    }

  test("the map's feed leaves the imprecise out and counts them; the list keeps them, with the class"):
    put(
      BEventRow.Id("zqcr_precise"),
      TimeSpan(Some(Year(-1610)), Some(Year(-1610)), Some(Year(-1610)), Some(Year(-1610)))
    )
    put(BEventRow.Id("zqcr_approx"), TimeSpan(Some(Year(-1800)), None, None, Some(Year(-1400))))
    put(BEventRow.Id("zqcr_imprecise"), TimeSpan(Some(Year(-4000)), None, None, Some(Year(1000))))
    val f = Filter(period = Some(Period(Year(-1700), Year(-1500))))
    val geo = EventFixtures.geo(
      ds,
      f,
      PlaceScope.empty,
      Some(EventSelection.Text("Zqcr zqcr_")),
      None,
      None,
      None,
      PageSize(100),
      EventFixtures.GeoTolerance(0.01)
    )
    assertEquals(
      geo.events.map(g => g.id -> g.precision).toSet,
      Set("zqcr_precise" -> EventPrecision.Precise, "zqcr_approx" -> EventPrecision.Approximate)
    )
    assert(geo.imprecise.value >= 1, geo.imprecise.toString)
    val listed = EventFixtures
      .list(
        ds,
        f,
        PlaceScope.empty,
        Some(EventSelection.Text("Zqcr zqcr_")),
        None,
        None,
        None,
        PageSize(100),
        Offset(0)
      )
      .events
      .map(e => e.id -> e.precision)
      .toMap
    assertEquals(listed.get(Event.Id("zqcr_imprecise")), Some(EventPrecision.Imprecise), "listed, not drawn")

  test("the merge reads stored rows in id order and places shuffled inputs identically"):
    val ids = Random(7).shuffle((1 to 6).map(i => BEventRow.Id(s"zqcr_m$i")).toList)
    ids.foreach(id =>
      put(
        id,
        TimeSpan(Some(Year(-900 - id.value.last.asDigit)), None, None, Some(Year(-890 + id.value.last.asDigit))),
        Longitude(161.0)
      )
    )
    val stored = EventFixtures.stored(ds)
    assertEquals(stored.map(_.id), stored.map(_.id).sorted)
    val mine = stored.filter(_.id.value.startsWith("zqcr_m"))
    val recs = (1 to 4)
      .map(i =>
        CatalogRecord(
          P,
          CatalogRecord.ProviderId(s"r$i"),
          CatalogRecord.Url(""),
          CatalogRecord.Label(s"r$i"),
          CatalogRecord.Kind("eruption"),
          TimeSpan(
            Some(Year(-900L + i)),
            Some(Year(-900L + i)),
            Some(Year(-900L + i)),
            Some(Year(-900L + i))
          ),
          Some(Latitude(-45.0)),
          Some(Longitude(161.01))
        )
      )
      .toList
    val tol = CatalogMerge.Tolerance(CatalogMerge.Tolerance.Km(60), CatalogMerge.Tolerance.Years(30))
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
    CatalogRecord.ProviderId("c1"),
    CatalogRecord.Url("https://example.org/c1"),
    CatalogRecord.Label("Eruption of Zqcr"),
    CatalogRecord.Kind("eruption"),
    TimeSpan(Some(Year(-2251)), None, None, Some(Year(2026))),
    Some(Latitude(-45.0)),
    Some(Longitude(162.0)),
    description = Some(CatalogRecord.Description("Confirmed Eruption at Zqcr")),
    central = Some(CatalogRecord.Central(250)),
    plusMinus = Some(CatalogRecord.PlusMinus(2500)),
    datingMethod = Some(CatalogRecord.DatingMethod("Tephrochronology"))
  )
  private val Id = BEventRow.Id("zzcr_c1")
  private val TargetId = Correction.TargetId("zzcr_c1")
  private val EventKind = Correction.TargetKind(Correction.Target.Event)

  private def reimport(r: CatalogRecord): EventSaved =
    EventFixtures.save(
      ds,
      CatalogMerge.place(
        List(r),
        EventFixtures.stored(ds),
        CatalogMerge.Tolerance(CatalogMerge.Tolerance.Km(60), CatalogMerge.Tolerance.Years(30))
      )
    )
  private def event = EventFixtures.get(ds, Id).get
  private def corrections =
    EventFixtures
      .corrections(ds, EventKind, Some(TargetId), Some(Correction.StatusValue("active")), PageSize(50), Offset(0))
      .corrections ++
      EventFixtures
        .corrections(ds, EventKind, Some(TargetId), Some(Correction.StatusValue("conflict")), PageSize(50), Offset(0))
        .corrections ++
      EventFixtures
        .corrections(ds, EventKind, Some(TargetId), Some(Correction.StatusValue("agreed")), PageSize(50), Offset(0))
        .corrections

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
            label = Some(EventSummary.Label("Hekla 3 (Zqcr)")),
            description = Some(EventSummary.Description("my note")),
            historicity = Some(EventSummary.Historicity(Historicity.Attested))
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
      corrections.find(_.field.value == EventFields.Label).map(c => Corrections.show(c.upstreamValue.value)),
      Some("Eruption of Zqcr")
    )
    val again = reimport(upstream0)
    assertEquals(
      (again.created, again.updated, again.corrections),
      (0, 1, Correction.Counts(kept = Correction.Counts.Kept(2)))
    )
    assertEquals(
      (event.event.label, event.event.description),
      ("Hekla 3 (Zqcr)", Some("my note")),
      "kept over the catalogue's"
    )
    assertEquals(event.corrections.size, 2, "the card lists them")

  test("corrections: retired once the catalogue agrees; flagged when it changes to something else"):
    val agrees = reimport(upstream0.modify(_.label).setTo(CatalogRecord.Label("Hekla 3 (Zqcr)")))
    assertEquals(
      agrees.corrections,
      Correction.Counts(kept = Correction.Counts.Kept(1), retired = Correction.Counts.Retired(1))
    )
    assertEquals(corrections.find(_.field.value == EventFields.Label).map(_.status), Some(Correction.Status.Agreed))
    assertEquals(event.event.label, "Hekla 3 (Zqcr)")
    // the catalogue now says something else about the description: the person's stays, flagged
    val changed =
      upstream0
        .modify(_.label)
        .setTo(CatalogRecord.Label("Hekla 3 (Zqcr)"))
        .modify(_.description)
        .setTo(Some(CatalogRecord.Description("Confirmed Eruption at Zqcr; area: SW flank")))
    val c = reimport(changed)
    assertEquals(c.corrections, Correction.Counts(conflicts = Correction.Counts.Conflicts(1)))
    assertEquals(event.event.description, Some("my note"))
    val conflict = EventFixtures
      .corrections(ds, EventKind, None, Some(Correction.StatusValue("conflict")), PageSize(50), Offset(0))
      .corrections
      .filter(_.targetId == TargetId)
    assertEquals(
      conflict.map(x => (x.field, x.upstreamNow.map(n => Corrections.show(n.value)))),
      List(EventFields.Description -> Some("Confirmed Eruption at Zqcr; area: SW flank"))
    )
    assertEquals(conflict.head.label, Some("Hekla 3 (Zqcr)"))
    val lint = connect(ds)(CorrectionRepo.conflictsIn())
    assert(lint.exists((id, detail) => id == TargetId && detail.value.startsWith("description:")), lint.toString)
    // still a conflict on the next import (the catalogue unchanged since), and back to active if it reverts
    assertEquals(reimport(changed).corrections, Correction.Counts(conflicts = Correction.Counts.Conflicts(1)))
    assertEquals(
      reimport(upstream0.modify(_.label).setTo(CatalogRecord.Label("Hekla 3 (Zqcr)"))).corrections,
      Correction.Counts(kept = Correction.Counts.Kept(1))
    )
    // setting the field to the catalogue's value takes the catalogue's: the correction goes
    assert(
      EventFixtures
        .patch(ds, Id, EventPatch(description = Some(EventSummary.Description("Confirmed Eruption at Zqcr"))))
        .isRight
    )
    assertEquals(corrections.filter(_.field.value == EventFields.Description), Nil)

  test("corrections: a span set by hand drops the stated year and ±, and survives; a rename and a delete carry them"):
    val mine = TimeSpan(Some(Year(1104)), Some(Year(1104)), Some(Year(1104)), Some(Year(1104)))
    assert(EventFixtures.patch(ds, Id, EventPatch(timeSpan = Some(mine))).isRight)
    assertEquals(
      (event.event.central, event.event.plusMinus, event.event.precision),
      (None, None, EventPrecision.Precise)
    )
    val _ = reimport(upstream0.modify(_.label).setTo(CatalogRecord.Label("Hekla 3 (Zqcr)")))
    assertEquals(
      (event.event.timeSpan, event.event.central),
      (mine, None),
      "the person's span, not the catalogue's ± window"
    )
    // an event compiled from sources records nothing: no import rewrites it
    put(BEventRow.Id("zqcr_compiled"), TimeSpan(Some(Year(1)), Some(Year(1)), Some(Year(1)), Some(Year(1))))
    assert(
      EventFixtures.patch(ds, BEventRow.Id("zqcr_compiled"), EventPatch(label = Some(EventSummary.Label("x")))).isRight
    )
    assertEquals(
      EventFixtures
        .corrections(ds, EventKind, Some(Correction.TargetId("zqcr_compiled")), None, PageSize(10), Offset(0))
        .total,
      0
    )
    // rename: the corrections follow the event
    assert(EventFixtures.rename(ds, Id, BEventRow.Id("zzcr_renamed")).isRight)
    assertEquals(
      EventFixtures
        .corrections(ds, EventKind, Some(Correction.TargetId("zzcr_renamed")), None, PageSize(10), Offset(0))
        .corrections
        .map(_.field),
      List(EventFields.Span)
    )
    // the renamed event is still refreshed from its owner record, the correction applied
    val _ = reimport(upstream0)
    assertEquals(EventFixtures.get(ds, BEventRow.Id("zzcr_renamed")).map(_.event.timeSpan), Some(mine))
    assert(EventFixtures.delete(ds, BEventRow.Id("zzcr_renamed")))
    assertEquals(
      EventFixtures
        .corrections(ds, EventKind, Some(Correction.TargetId("zzcr_renamed")), None, PageSize(10), Offset(0))
        .total,
      0
    )
