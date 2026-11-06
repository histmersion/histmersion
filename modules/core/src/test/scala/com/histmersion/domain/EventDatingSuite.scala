package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.TimeSpan
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import io.circe.Json
import io.circe.syntax.*

import scala.util.Random
import com.histmersion.api.catalogue.{
  CatalogRecord,
  Correction,
  Corrections,
  EventFields,
  EventPatch,
  EventPrecision,
  EventPut
}

/** The data-source assessment's P0 fixes, the host's pure half: the precision classes and the dating note; the
  * corrections' reconcile table and the event fields compared; and a merge that does not depend on the order of its
  * inputs. The catalogues' datings and re-reading are their plugins' suites'.
  */
class EventDatingSuite extends munit.FunSuite:

  test("a dating note: the stated year, ± and method as the card shows them"):
    assertEquals(
      EventPrecision.note(Some(com.histmersion.api.catalogue.EventSummary.Central(250)), Some(com.histmersion.api.catalogue.EventSummary.PlusMinus(2500)), Some(com.histmersion.api.catalogue.EventSummary.DatingMethod("Tephrochronology"))),
      Some("250 CE ± 2500 (tephrochronology)")
    )
    assertEquals(
      EventPrecision.note(Some(com.histmersion.api.catalogue.EventSummary.Central(-4050)), Some(com.histmersion.api.catalogue.EventSummary.PlusMinus(3000)), Some(com.histmersion.api.catalogue.EventSummary.DatingMethod("Isotopic: K/Ar"))),
      Some("4050 BCE ± 3000 (isotopic: K/Ar)")
    )
    assertEquals(EventPrecision.note(Some(com.histmersion.api.catalogue.EventSummary.Central(79)), None, None), Some("79 CE"))
    assertEquals(
      EventPrecision.note(None, None, Some(com.histmersion.api.catalogue.EventSummary.DatingMethod("Historical Observations"))),
      Some("dated by historical observations")
    )

  test("precision: ± ≤ 50 precise, ≤ 500 approximate, more imprecise; the stated ± or the widest window"):
    def p(ts: TimeSpan, pm: Option[com.histmersion.api.catalogue.EventSummary.PlusMinus] = None) = EventPrecision.of(ts, pm)
    assertEquals(p(TimeSpan(Some(com.histmersion.api.catalogue.Year(79)), Some(com.histmersion.api.catalogue.Year(79)), Some(com.histmersion.api.catalogue.Year(79)), Some(com.histmersion.api.catalogue.Year(79)))), EventPrecision.Precise)
    assertEquals(p(TimeSpan(Some(com.histmersion.api.catalogue.Year(-1624)), None, None, Some(com.histmersion.api.catalogue.Year(-1596)))), EventPrecision.Precise, "± 14")
    assertEquals(
      p(TimeSpan(Some(com.histmersion.api.catalogue.Year(-1700)), None, None, Some(com.histmersion.api.catalogue.Year(-1599)))),
      EventPrecision.Precise,
      "a century-wide window: ± 50"
    )
    assertEquals(EventPrecision.uncertainty(TimeSpan(Some(com.histmersion.api.catalogue.Year(-1700)), None, None, Some(com.histmersion.api.catalogue.Year(-1599))), None), Some(50L))
    assertEquals(EventPrecision.uncertainty(TimeSpan(Some(com.histmersion.api.catalogue.Year(-1700)), None, None, Some(com.histmersion.api.catalogue.Year(-1598))), None), Some(51L))
    assertEquals(
      p(TimeSpan(Some(com.histmersion.api.catalogue.Year(-1000)), None, None, Some(com.histmersion.api.catalogue.Year(-1))), None),
      EventPrecision.Approximate,
      "a millennium: ± 499"
    )
    assertEquals(p(TimeSpan(Some(com.histmersion.api.catalogue.Year(-1000)), None, None, Some(com.histmersion.api.catalogue.Year(1)))), EventPrecision.Approximate, "across year 0: ± 500")
    assertEquals(p(TimeSpan(Some(com.histmersion.api.catalogue.Year(-1000)), None, None, Some(com.histmersion.api.catalogue.Year(3)))), EventPrecision.Imprecise, "± 501")
    assertEquals(EventPrecision.uncertainty(TimeSpan(Some(com.histmersion.api.catalogue.Year(-1000)), None, None, Some(com.histmersion.api.catalogue.Year(1))), None), Some(500L), "no year 0")
    // a long war with a certain core is precisely dated, however long it lasted
    assertEquals(p(TimeSpan(Some(com.histmersion.api.catalogue.Year(-431)), Some(com.histmersion.api.catalogue.Year(-431)), Some(com.histmersion.api.catalogue.Year(-404)), Some(com.histmersion.api.catalogue.Year(-404)))), EventPrecision.Precise)
    // a cored event's widest uncertain window
    assertEquals(
      EventPrecision.uncertainty(TimeSpan(Some(com.histmersion.api.catalogue.Year(-1300)), Some(com.histmersion.api.catalogue.Year(-1100)), Some(com.histmersion.api.catalogue.Year(-1000)), Some(com.histmersion.api.catalogue.Year(-990))), None),
      Some(100L)
    )
    // the stated ± wins where it is larger than what the (clamped) window shows
    assertEquals(EventPrecision.uncertainty(TimeSpan(Some(com.histmersion.api.catalogue.Year(-2251)), None, None, Some(com.histmersion.api.catalogue.Year(2026))), Some(com.histmersion.api.catalogue.EventSummary.PlusMinus(2500))), Some(2500L))
    assertEquals(p(TimeSpan()), EventPrecision.Undated)
    assertEquals(
      List(EventPrecision.Precise, EventPrecision.Approximate, EventPrecision.Imprecise, EventPrecision.Undated)
        .map(k => EventPrecision.mapped(com.histmersion.api.catalogue.EventSummary.Precision(k))),
      List(true, true, false, false)
    )
    assertEquals(EventPrecision.label(com.histmersion.api.catalogue.EventSummary.Precision(EventPrecision.Imprecise)), Some("imprecise date"))

  // ── corrections ────────────────────────────────────────────────────────────────────────────

  test("corrections: upstream unchanged keeps, upstream agreeing retires, upstream elsewhere conflicts"):
    val (x, y, z) = ("Eruption of Hekla".asJson, "Hekla 3".asJson, "Eruption of Hekla volcano".asJson)
    assertEquals(Corrections.reconcile(y, x, x), Corrections.Outcome.Keep)
    assertEquals(Corrections.reconcile(y, x, y), Corrections.Outcome.Retire)
    assertEquals(Corrections.reconcile(y, x, z), Corrections.Outcome.Conflict)
    assertEquals(Corrections.status(Corrections.Outcome.Conflict), Correction.Status.Conflict)
    assertEquals(
      Corrections.counts(List(Corrections.Outcome.Keep, Corrections.Outcome.Conflict, Corrections.Outcome.Keep)),
      Correction.Counts(com.histmersion.api.catalogue.Correction.Counts.Kept(2), com.histmersion.api.catalogue.Correction.Counts.Retired(0), com.histmersion.api.catalogue.Correction.Counts.Conflicts(1))
    )
    val c = Correction(
      com.histmersion.api.catalogue.Correction.Id(1),
      com.histmersion.api.catalogue.Correction.TargetKind("event"),
      com.histmersion.api.catalogue.Correction.TargetId("gvp_1"),
      com.histmersion.api.catalogue.Correction.Field("label"),
      com.histmersion.api.catalogue.Correction.Value(y.noSpaces),
      com.histmersion.api.catalogue.Correction.UpstreamValue(x.noSpaces),
      com.histmersion.api.catalogue.Correction.StatusValue(Correction.Status.Conflict),
      Some(com.histmersion.api.catalogue.Correction.Upstream("gvp")),
      Some(com.histmersion.api.catalogue.Correction.UpstreamNow(z.noSpaces))
    )
    assertEquals(
      Corrections.describe(c),
      "label: “Hekla 3” (was “Eruption of Hekla” in gvp) — CONFLICT: the catalogue now says “Eruption of Hekla volcano”"
    )

  test("event fields: a patch and the catalogue's record compare field by field, as JSON"):
    val r = CatalogRecord(
      com.histmersion.api.catalogue.CatalogRecord.Provider("gvp"),
      com.histmersion.api.catalogue.CatalogRecord.ProviderId("1"),
      com.histmersion.api.catalogue.CatalogRecord.Url(""),
      com.histmersion.api.catalogue.CatalogRecord.Label("Eruption of Zz"),
      com.histmersion.api.catalogue.CatalogRecord.Kind("volcanic eruption"),
      TimeSpan(Some(com.histmersion.api.catalogue.Year(1)), None, None, Some(com.histmersion.api.catalogue.Year(3))),
      Some(com.histmersion.api.catalogue.Latitude(10.5)),
      Some(com.histmersion.api.catalogue.Longitude(20.25)),
      description = Some(com.histmersion.api.catalogue.CatalogRecord.Description("x"))
    )
    val up = EventFields.upstream(r)
    assertEquals(up.keySet, EventFields.all.toSet)
    assertEquals(up(EventFields.Kind), "eruption".asJson, "canonical")
    val p = EventFields.ofPatch(
      EventPatch(
        label = Some(com.histmersion.api.catalogue.EventSummary.Label("Zz")),
        kind = Some(com.histmersion.api.catalogue.EventSummary.Kind("Volcanic eruption")),
        geometry = Some(EventPatch.Geometry("""{"type":"Point","coordinates":[20.25, 10.5]}""")),
        description = Some(com.histmersion.api.catalogue.EventSummary.Description(""))
      )
    )
    assertEquals(p.keySet, Set(EventFields.Label, EventFields.Kind, EventFields.Geometry, EventFields.Description))
    assertEquals(p(EventFields.Geometry), up(EventFields.Geometry), "the same point, whatever the spacing")
    assertEquals(p(EventFields.Kind), up(EventFields.Kind))
    assertEquals(p(EventFields.Description), Json.Null, "an empty description clears it")
    assertEquals(EventFields.ofPut(EventPut(com.histmersion.api.catalogue.EventSummary.Label("Zz"))).keySet, EventFields.all.toSet)

  // ── deterministic merge ────────────────────────────────────────────────────────────────────

  private val tol = CatalogMerge.Tolerance(com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Km(60), com.histmersion.domain.catalogue.events.CatalogMerge.Tolerance.Years(30))
  private def rec(
      p: CatalogRecord.Provider,
      id: CatalogRecord.ProviderId,
      y: com.histmersion.api.catalogue.Year,
      lat: com.histmersion.api.catalogue.Latitude,
      lon: com.histmersion.api.catalogue.Longitude,
      group: Option[CatalogRecord.Group] = None
  ) =
    CatalogRecord(
      p,
      id,
      com.histmersion.api.catalogue.CatalogRecord.Url(""),
      CatalogRecord.Label(s"$p $id"),
      com.histmersion.api.catalogue.CatalogRecord.Kind("eruption"),
      TimeSpan(Some(y), Some(y), Some(y), Some(y)),
      Some(lat),
      Some(lon),
      group = group
    )
  private def ev(
      id: com.histmersion.domain.catalogue.Event.Id,
      y0: com.histmersion.api.catalogue.Year,
      y1: com.histmersion.api.catalogue.Year,
      lat: com.histmersion.Latitude,
      lon: com.histmersion.Longitude,
      wd: Option[com.histmersion.api.catalogue.CatalogRecord.Wikidata] = None
  ) =
    StoredEvent(
      id,
      com.histmersion.domain.catalogue.events.StoredEvent.Kind("eruption"),
      TimeSpan(Some(y0), None, None, Some(y1)),
      Some(lat),
      Some(lon),
      wd,
      Set.empty,
      catalogue = com.histmersion.domain.catalogue.events.StoredEvent.Catalogue(false)
    )

  test(
    "merge: several stored events qualify — the best fit wins (overlap, then |Δt|, then km, then id), never the first"
  ):
    // both overlap -1610 within the tolerance; `zz_b` fits its span better
    val a = ev(com.histmersion.domain.catalogue.Event.Id("zz_a"), com.histmersion.api.catalogue.Year(-1700), com.histmersion.api.catalogue.Year(-1500), com.histmersion.Latitude(36.4), com.histmersion.Longitude(25.4))
    val b = ev(com.histmersion.domain.catalogue.Event.Id("zz_b"), com.histmersion.api.catalogue.Year(-1615), com.histmersion.api.catalogue.Year(-1605), com.histmersion.Latitude(36.45), com.histmersion.Longitude(25.45))
    val r = rec(CatalogRecord.Provider("gvp"), CatalogRecord.ProviderId("1"), com.histmersion.api.catalogue.Year(-1610), com.histmersion.api.catalogue.Latitude(36.4), com.histmersion.api.catalogue.Longitude(25.4))
    assertEquals(CatalogMerge.place(List(r), List(a, b), tol).head._2.eventId, "zz_b")
    assertEquals(CatalogMerge.place(List(r), List(b, a), tol).head._2.eventId, "zz_b")
    // the same span: the nearer wins; the same span and point: the smaller id
    val c = ev(com.histmersion.domain.catalogue.Event.Id("zz_c"), com.histmersion.api.catalogue.Year(-1615), com.histmersion.api.catalogue.Year(-1605), com.histmersion.Latitude(36.40), com.histmersion.Longitude(25.40))
    assertEquals(CatalogMerge.place(List(r), List(b, c), tol).head._2.eventId, "zz_c")
    val d = ev(com.histmersion.domain.catalogue.Event.Id("zz_0"), com.histmersion.api.catalogue.Year(-1615), com.histmersion.api.catalogue.Year(-1605), com.histmersion.Latitude(36.40), com.histmersion.Longitude(25.40))
    assertEquals(CatalogMerge.place(List(r), List(c, d), tol).head._2.eventId, "zz_0")
    // two stored events of one Wikidata item: the best fit, not whichever came last
    val w1 = ev(com.histmersion.domain.catalogue.Event.Id("zz_w1"), com.histmersion.api.catalogue.Year(-1615), com.histmersion.api.catalogue.Year(-1605), com.histmersion.Latitude(36.4), com.histmersion.Longitude(25.4), Some(com.histmersion.api.catalogue.EventEntry.Wikidata("Q1")))
    val w2 = ev(com.histmersion.domain.catalogue.Event.Id("zz_w2"), com.histmersion.api.catalogue.Year(-1200), com.histmersion.api.catalogue.Year(-1100), com.histmersion.Latitude(36.4), com.histmersion.Longitude(25.4), Some(com.histmersion.api.catalogue.EventEntry.Wikidata("Q1")))
    val rw = rec(CatalogRecord.Provider("wikidata"), CatalogRecord.ProviderId("Q1"), com.histmersion.api.catalogue.Year(-1610), com.histmersion.api.catalogue.Latitude(36.4), com.histmersion.api.catalogue.Longitude(25.4)).modify(_.wikidata).setTo(Some(com.histmersion.api.catalogue.EventEntry.Wikidata("Q1")))
    assertEquals(CatalogMerge.place(List(rw), List(w1, w2), tol).head._2.eventId, "zz_w1")
    assertEquals(CatalogMerge.place(List(rw), List(w2, w1), tol).head._2.eventId, "zz_w1")

  test("merge: shuffled records and shuffled stored events place identically"):
    val records = List(
      rec(CatalogRecord.Provider("gvp"), CatalogRecord.ProviderId("1"), com.histmersion.api.catalogue.Year(-1610), com.histmersion.api.catalogue.Latitude(36.4), com.histmersion.api.catalogue.Longitude(25.4), Some(CatalogRecord.Group("volcano:1"))),
      rec(CatalogRecord.Provider("gvp"), CatalogRecord.ProviderId("2"), com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Latitude(36.4), com.histmersion.api.catalogue.Longitude(25.4), Some(CatalogRecord.Group("volcano:1"))),
      rec(CatalogRecord.Provider("noaa"), CatalogRecord.ProviderId("volcano:5"), com.histmersion.api.catalogue.Year(-1608), com.histmersion.api.catalogue.Latitude(36.41), com.histmersion.api.catalogue.Longitude(25.39), Some(CatalogRecord.Group("volcano:1"))),
      rec(CatalogRecord.Provider("noaa"), CatalogRecord.ProviderId("volcano:6"), com.histmersion.api.catalogue.Year(-1601), com.histmersion.api.catalogue.Latitude(36.41), com.histmersion.api.catalogue.Longitude(25.39), Some(CatalogRecord.Group("volcano:1"))),
      rec(CatalogRecord.Provider("wikidata"), CatalogRecord.ProviderId("Q9"), com.histmersion.api.catalogue.Year(-1605), com.histmersion.api.catalogue.Latitude(36.40), com.histmersion.api.catalogue.Longitude(25.41)),
      rec(CatalogRecord.Provider("gvp"), CatalogRecord.ProviderId("3"), com.histmersion.api.catalogue.Year(-79), com.histmersion.api.catalogue.Latitude(40.8), com.histmersion.api.catalogue.Longitude(14.4)),
      rec(CatalogRecord.Provider("noaa"), CatalogRecord.ProviderId("volcano:7"), com.histmersion.api.catalogue.Year(-79), com.histmersion.api.catalogue.Latitude(40.82), com.histmersion.api.catalogue.Longitude(14.42))
    )
    val stored = List(
      ev(com.histmersion.domain.catalogue.Event.Id("zz_thera"), com.histmersion.api.catalogue.Year(-1650), com.histmersion.api.catalogue.Year(-1550), com.histmersion.Latitude(36.4), com.histmersion.Longitude(25.4)),
      ev(com.histmersion.domain.catalogue.Event.Id("zz_thera2"), com.histmersion.api.catalogue.Year(-1620), com.histmersion.api.catalogue.Year(-1590), com.histmersion.Latitude(36.42), com.histmersion.Longitude(25.42)),
      ev(com.histmersion.domain.catalogue.Event.Id("zz_vesuvius"), com.histmersion.api.catalogue.Year(-80), com.histmersion.api.catalogue.Year(-78), com.histmersion.Latitude(40.8), com.histmersion.Longitude(14.4))
    )
    val expected = CatalogMerge.place(records, stored, tol).map((r, p) => r.key -> p).toMap
    val rnd = Random(1449)
    (1 to 30).foreach { _ =>
      val got = CatalogMerge.place(rnd.shuffle(records), rnd.shuffle(stored), tol)
      assertEquals(got.map((r, p) => r.key -> p).toMap, expected)
    }
    // and the answer keeps the order it was given
    val shuffled = rnd.shuffle(records)
    assertEquals(CatalogMerge.place(shuffled, stored, tol).map(_._1), shuffled)

  test("the owner record of an event: the one its id is named after, else the lowest of its catalogue's"):
    assertEquals(
      CatalogRecord.owner(com.histmersion.api.catalogue.CatalogRecord.EventId("gvp_2"), com.histmersion.api.catalogue.CatalogRecord.Provider("gvp"), List(CatalogRecord.Provider("gvp") -> com.histmersion.api.catalogue.EventCatalogueRef.ProviderId("1"), CatalogRecord.Provider("gvp") -> com.histmersion.api.catalogue.EventCatalogueRef.ProviderId("2"), CatalogRecord.Provider("noaa") -> com.histmersion.api.catalogue.EventCatalogueRef.ProviderId("x"))),
      Some(CatalogRecord.Provider("gvp") -> com.histmersion.api.catalogue.EventCatalogueRef.ProviderId("2"))
    )
    assertEquals(
      CatalogRecord.owner(com.histmersion.api.catalogue.CatalogRecord.EventId("renamed"), com.histmersion.api.catalogue.CatalogRecord.Provider("gvp"), List(CatalogRecord.Provider("noaa") -> com.histmersion.api.catalogue.EventCatalogueRef.ProviderId("x"), CatalogRecord.Provider("gvp") -> com.histmersion.api.catalogue.EventCatalogueRef.ProviderId("9"), CatalogRecord.Provider("gvp") -> com.histmersion.api.catalogue.EventCatalogueRef.ProviderId("10"))),
      Some(CatalogRecord.Provider("gvp") -> com.histmersion.api.catalogue.EventCatalogueRef.ProviderId("10"))
    )
    assertEquals(CatalogRecord.owner(com.histmersion.api.catalogue.CatalogRecord.EventId("x"), com.histmersion.api.catalogue.CatalogRecord.Provider("gvp"), List(CatalogRecord.Provider("noaa") -> com.histmersion.api.catalogue.EventCatalogueRef.ProviderId("x"))), None)

  test("an item read in two Wikidata classes keeps the more specific kind, whichever class is read last"):
    val order = List("naval_battle", "siege", "battle", "revolt", "war").map(k => com.histmersion.api.catalogue.EventSummary.Kind(k))
    val more = EventProviders.WikidataEvents.moreSpecific(order, _, _)
    assertEquals(more(com.histmersion.api.catalogue.EventSummary.Kind("naval_battle"), com.histmersion.api.catalogue.EventSummary.Kind("battle")), "naval_battle")
    assertEquals(more(com.histmersion.api.catalogue.EventSummary.Kind("battle"), com.histmersion.api.catalogue.EventSummary.Kind("naval_battle")), "naval_battle")
    assertEquals(more(com.histmersion.api.catalogue.EventSummary.Kind("war"), com.histmersion.api.catalogue.EventSummary.Kind("revolt")), "revolt")
    assertEquals(more(com.histmersion.api.catalogue.EventSummary.Kind("battle"), com.histmersion.api.catalogue.EventSummary.Kind("natural_disaster")), "battle", "a kind outside the classes yields")
    assertEquals(more(com.histmersion.api.catalogue.EventSummary.Kind("natural_disaster"), com.histmersion.api.catalogue.EventSummary.Kind("battle")), "battle")
