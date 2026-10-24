package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given


import io.circe.Json
import io.circe.syntax.*

import scala.util.Random

/** The data-source assessment's P0 fixes, the pure half: GVP datings (the five records of §4) keep
  * their central year, ± and method and are held inside the present and the Holocene; the precision
  * classes; re-derivation from a stored record (raw, and a record stored before V088); the
  * corrections' reconcile table and the event fields compared; and a merge that does not depend on
  * the order of its inputs. */
class EventDatingSuite extends munit.FunSuite:

  private val limits = CatalogDating.Limits(present = 2026, floor = Some(-9700))

  /** A GVP WFS feature as the service answers (2026-10-16), `EndDateYear` null. */
  private def feature(num: Int, volcano: String, year: Long, pm: Long, method: String): Json =
    io.circe.parser.parse(
      s"""{"type":"Feature","id":"Smithsonian_VOTW_Holocene_Eruptions.fid-$num","geometry":{"type":"Point","coordinates":[-19.7,63.98]},
         |"properties":{"Volcano_Number":372070,"Volcano_Name":"$volcano","Eruption_Number":$num,"Activity_Type":"Confirmed Eruption",
         |"ExplosivityIndexMax":null,"StartEvidenceMethod":"$method","StartDateYearModifier":null,"StartDateYear":$year,
         |"StartDateYearUncertainty":$pm,"EndDateYear":null,"EndDateYearUncertainty":null}}""".stripMargin).toOption.get

  private val samples = List(
    // eruption, volcano, year, ±, method, the span expected once clamped
    (12724, "Hekla", 250L, 2500L, "Tephrochronology", (-2251L, 2026L)),
    (12725, "Hekla", 350L, 1500L, "Tephrochronology", (-1151L, 1850L)),
    (12693, "Katla", -4950L, 2500L, "Tephrochronology", (-7450L, -2450L)),
    (13048, "La Palma", -4050L, 3000L, "Isotopic: K/Ar", (-7050L, -1050L)),
    (18283, "Hachijojima", -8020L, 2640L, "Tephrochronology", (-9700L, -5380L)))

  test("GVP: the five records of §4 keep their year, ± and method; the span is held inside the present and the Holocene"):
    samples.foreach { case (num, volcano, year, pm, method, (lo, hi)) =>
      val r = CatalogDating.clamp(EventProviders.Gvp.record(feature(num, volcano, year, pm, method)).get, limits)
      assertEquals((r.central, r.plusMinus, r.datingMethod), (Some(year), Some(pm), Some(method)), s"$num")
      assertEquals(r.timeSpan, TimeSpan(Some(lo), None, None, Some(hi)), s"$num: no certain core is invented")
      assert(r.timeSpan.notAfter.forall(_ <= 2026), s"$num ends after today")
      assertEquals(EventPrecision.of(r.timeSpan, r.plusMinus), EventPrecision.Imprecise, s"$num")
      assert(r.raw.isDefined && !r.description.exists(_.contains("dated by")), s"$num: the method is a field, not prose")
    }
    val hekla = EventProviders.Gvp.record(feature(12724, "Hekla", 250, 2500, "Tephrochronology")).get
    assertEquals(EventPrecision.note(hekla.central, hekla.plusMinus, hekla.datingMethod), Some("250 CE ± 2500 (tephrochronology)"))
    assertEquals(EventPrecision.note(Some(-4050), Some(3000), Some("Isotopic: K/Ar")), Some("4050 BCE ± 3000 (isotopic: K/Ar)"))
    assertEquals(EventPrecision.note(Some(79), None, None), Some("79 CE"))
    assertEquals(EventPrecision.note(None, None, Some("Historical Observations")), Some("dated by historical observations"))

  test("clamp: the present for every catalogue, the Holocene floor for the GVP only; monotone"):
    val wide = TimeSpan(Some(-11000), Some(-10000), Some(1990), Some(2100))
    assertEquals(CatalogDating.clamp(wide, limits), TimeSpan(Some(-9700), Some(-9700), Some(1990), Some(2026)))
    assertEquals(CatalogDating.clamp(wide, EventProviders.limits(CatalogProvider.Noaa, 2026, -9700)),
      TimeSpan(Some(-11000), Some(-10000), Some(1990), Some(2026)))
    assertEquals(EventProviders.limits(CatalogProvider.Gvp, 2026, -9700), limits)

  test("precision: ± ≤ 50 precise, ≤ 500 approximate, more imprecise; the stated ± or the widest window"):
    def p(ts: TimeSpan, pm: Option[Long] = None) = EventPrecision.of(ts, pm)
    assertEquals(p(TimeSpan(Some(79), Some(79), Some(79), Some(79))), EventPrecision.Precise)
    assertEquals(p(TimeSpan(Some(-1624), None, None, Some(-1596))), EventPrecision.Precise, "± 14")
    assertEquals(p(TimeSpan(Some(-1700), None, None, Some(-1599))), EventPrecision.Precise, "a century-wide window: ± 50")
    assertEquals(EventPrecision.uncertainty(TimeSpan(Some(-1700), None, None, Some(-1599)), None), Some(50L))
    assertEquals(EventPrecision.uncertainty(TimeSpan(Some(-1700), None, None, Some(-1598)), None), Some(51L))
    assertEquals(p(TimeSpan(Some(-1000), None, None, Some(-1)), None), EventPrecision.Approximate, "a millennium: ± 499")
    assertEquals(p(TimeSpan(Some(-1000), None, None, Some(1))), EventPrecision.Approximate, "across year 0: ± 500")
    assertEquals(p(TimeSpan(Some(-1000), None, None, Some(3))), EventPrecision.Imprecise, "± 501")
    assertEquals(EventPrecision.uncertainty(TimeSpan(Some(-1000), None, None, Some(1)), None), Some(500L), "no year 0")
    // a long war with a certain core is precisely dated, however long it lasted
    assertEquals(p(TimeSpan(Some(-431), Some(-431), Some(-404), Some(-404))), EventPrecision.Precise)
    // a cored event's widest uncertain window
    assertEquals(EventPrecision.uncertainty(TimeSpan(Some(-1300), Some(-1100), Some(-1000), Some(-990)), None), Some(100L))
    // the stated ± wins where it is larger than what the (clamped) window shows
    assertEquals(EventPrecision.uncertainty(TimeSpan(Some(-2251), None, None, Some(2026)), Some(2500)), Some(2500L))
    assertEquals(p(TimeSpan()), EventPrecision.Undated)
    assertEquals(List(EventPrecision.Precise, EventPrecision.Approximate, EventPrecision.Imprecise, EventPrecision.Undated)
      .map(EventPrecision.mapped), List(true, true, false, false))
    assertEquals(EventPrecision.label(EventPrecision.Imprecise), Some("imprecise date"))

  test("re-derive: from the catalogue's own record, and from a record stored before V088 (its span read back)"):
    val parsed = EventProviders.Gvp.record(feature(12724, "Hekla", 250, 2500, "Tephrochronology")).get
    // a record as V085 stored it: the whole ± window, the method in the description, no raw
    val legacy = parsed.copy(timeSpan = TimeSpan(Some(-2251), None, None, Some(2750)), central = None, plusMinus = None,
      datingMethod = None, raw = None, description = Some("Confirmed Eruption at Hekla; dated by Tephrochronology"))
    val fromLegacy = EventProviders.rederive(legacy, limits)
    assertEquals((fromLegacy.central, fromLegacy.plusMinus, fromLegacy.datingMethod), (Some(250L), Some(2500L), Some("Tephrochronology")))
    assertEquals(fromLegacy.timeSpan, TimeSpan(Some(-2251), None, None, Some(2026)))
    assertEquals(fromLegacy.description, Some("Confirmed Eruption at Hekla"))
    val fromRaw = EventProviders.rederive(parsed.copy(timeSpan = TimeSpan(Some(0), None, None, Some(0)), names = List("Hekla tephra")), limits)
    assertEquals(fromRaw.timeSpan, TimeSpan(Some(-2251), None, None, Some(2026)), "parsed again: the stored span is not trusted")
    assert(fromRaw.names.contains("Hekla tephra") && fromRaw.names.contains("Hekla eruption"), fromRaw.names.toString)
    // exact years read back as exact; a cored start window centred
    assertEquals(CatalogDating.centralOf(TimeSpan(Some(79), Some(79), Some(79), Some(79))), Some((79L, 0L)))
    assertEquals(CatalogDating.centralOf(TimeSpan(Some(-510), Some(-490), Some(-460), Some(-440))), Some((-500L, 10L)))
    // a NOAA record is only clamped
    val noaa = CatalogRecord("noaa", "earthquake:1", "", "Earthquake", "earthquake", TimeSpan(Some(-464), Some(-464), Some(-464), Some(-464)), None, None)
    assertEquals(EventProviders.rederive(noaa, EventProviders.limits("noaa", 2026, -9700)), noaa)

  test("re-derive: NOAA and Wikidata records parse again from their raw record"):
    val (eq, _) = EventProviders.Noaa.parse("earthquakes", fixture("noaa_earthquakes.json")).toOption.get
    val one = eq.head
    assert(one.raw.isDefined && one.central.isDefined)
    assertEquals(EventProviders.rederive(one.copy(timeSpan = TimeSpan()), EventProviders.limits("noaa", 2026, -9700)).timeSpan, one.timeSpan)
    val wd = EventProviders.WikidataEvents.parse(fixture("wikidata_siege.json"), "siege").toOption.get.head
    assert(wd.raw.isDefined)
    assertEquals(EventProviders.rederive(wd.copy(timeSpan = TimeSpan()), EventProviders.limits("wikidata", 2026, -9700)).timeSpan, wd.timeSpan)

  private def fixture(name: String): Json =
    io.circe.parser.parse(scala.io.Source.fromResource(s"events/$name")(using scala.io.Codec.UTF8).mkString).toOption.get

  // ── corrections ────────────────────────────────────────────────────────────────────────────

  test("corrections: upstream unchanged keeps, upstream agreeing retires, upstream elsewhere conflicts"):
    val (x, y, z) = ("Eruption of Hekla".asJson, "Hekla 3".asJson, "Eruption of Hekla volcano".asJson)
    assertEquals(Corrections.reconcile(y, x, x), Corrections.Outcome.Keep)
    assertEquals(Corrections.reconcile(y, x, y), Corrections.Outcome.Retire)
    assertEquals(Corrections.reconcile(y, x, z), Corrections.Outcome.Conflict)
    assertEquals(Corrections.status(Corrections.Outcome.Conflict), CorrectionStatus.Conflict)
    assertEquals(Corrections.counts(List(Corrections.Outcome.Keep, Corrections.Outcome.Conflict, Corrections.Outcome.Keep)),
      CorrectionCounts(2, 0, 1))
    val c = Correction(1, "event", "gvp_1", "label", y.noSpaces, x.noSpaces, CorrectionStatus.Conflict, Some("gvp"), Some(z.noSpaces))
    assertEquals(Corrections.describe(c),
      "label: “Hekla 3” (was “Eruption of Hekla” in gvp) — CONFLICT: the catalogue now says “Eruption of Hekla volcano”")

  test("event fields: a patch and the catalogue's record compare field by field, as JSON"):
    val r = CatalogRecord("gvp", "1", "", "Eruption of Zz", "volcanic eruption", TimeSpan(Some(1), None, None, Some(3)),
      Some(10.5), Some(20.25), description = Some("x"))
    val up = EventFields.upstream(r)
    assertEquals(up.keySet, EventFields.all.toSet)
    assertEquals(up(EventFields.Kind), "eruption".asJson, "canonical")
    val p = EventFields.ofPatch(EventPatch(label = Some("Zz"), kind = Some("Volcanic eruption"),
      geometry = Some("""{"type":"Point","coordinates":[20.25, 10.5]}"""), description = Some("")))
    assertEquals(p.keySet, Set(EventFields.Label, EventFields.Kind, EventFields.Geometry, EventFields.Description))
    assertEquals(p(EventFields.Geometry), up(EventFields.Geometry), "the same point, whatever the spacing")
    assertEquals(p(EventFields.Kind), up(EventFields.Kind))
    assertEquals(p(EventFields.Description), Json.Null, "an empty description clears it")
    assertEquals(EventFields.ofPut(EventPut("Zz")).keySet, EventFields.all.toSet)

  // ── deterministic merge ────────────────────────────────────────────────────────────────────

  private val tol = CatalogMerge.Tolerance(60, 30)
  private def rec(p: String, id: String, y: Long, lat: Double, lon: Double, group: Option[String] = None) =
    CatalogRecord(p, id, "", s"$p $id", "eruption", TimeSpan(Some(y), Some(y), Some(y), Some(y)), Some(lat), Some(lon), group = group)
  private def ev(id: String, y0: Long, y1: Long, lat: Double, lon: Double, wd: Option[String] = None) =
    StoredEvent(id, "eruption", TimeSpan(Some(y0), None, None, Some(y1)), Some(lat), Some(lon), wd, Set.empty, catalogue = false)

  test("merge: several stored events qualify — the best fit wins (overlap, then |Δt|, then km, then id), never the first"):
    // both overlap -1610 within the tolerance; `zz_b` fits its span better
    val a = ev("zz_a", -1700, -1500, 36.4, 25.4)
    val b = ev("zz_b", -1615, -1605, 36.45, 25.45)
    val r = rec("gvp", "1", -1610, 36.4, 25.4)
    assertEquals(CatalogMerge.place(List(r), List(a, b), tol).head._2.eventId, "zz_b")
    assertEquals(CatalogMerge.place(List(r), List(b, a), tol).head._2.eventId, "zz_b")
    // the same span: the nearer wins; the same span and point: the smaller id
    val c = ev("zz_c", -1615, -1605, 36.40, 25.40)
    assertEquals(CatalogMerge.place(List(r), List(b, c), tol).head._2.eventId, "zz_c")
    val d = ev("zz_0", -1615, -1605, 36.40, 25.40)
    assertEquals(CatalogMerge.place(List(r), List(c, d), tol).head._2.eventId, "zz_0")
    // two stored events of one Wikidata item: the best fit, not whichever came last
    val w1 = ev("zz_w1", -1615, -1605, 36.4, 25.4, Some("Q1"))
    val w2 = ev("zz_w2", -1200, -1100, 36.4, 25.4, Some("Q1"))
    val rw = rec("wikidata", "Q1", -1610, 36.4, 25.4).copy(wikidata = Some("Q1"))
    assertEquals(CatalogMerge.place(List(rw), List(w1, w2), tol).head._2.eventId, "zz_w1")
    assertEquals(CatalogMerge.place(List(rw), List(w2, w1), tol).head._2.eventId, "zz_w1")

  test("merge: shuffled records and shuffled stored events place identically"):
    val records = List(
      rec("gvp", "1", -1610, 36.4, 25.4, Some("volcano:1")),
      rec("gvp", "2", -1600, 36.4, 25.4, Some("volcano:1")),
      rec("noaa", "volcano:5", -1608, 36.41, 25.39, Some("volcano:1")),
      rec("noaa", "volcano:6", -1601, 36.41, 25.39, Some("volcano:1")),
      rec("wikidata", "Q9", -1605, 36.40, 25.41),
      rec("gvp", "3", -79, 40.8, 14.4),
      rec("noaa", "volcano:7", -79, 40.82, 14.42))
    val stored = List(ev("zz_thera", -1650, -1550, 36.4, 25.4), ev("zz_thera2", -1620, -1590, 36.42, 25.42),
      ev("zz_vesuvius", -80, -78, 40.8, 14.4))
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
    assertEquals(CatalogRecord.owner("gvp_2", "gvp", List("gvp" -> "1", "gvp" -> "2", "noaa" -> "x")), Some("gvp" -> "2"))
    assertEquals(CatalogRecord.owner("renamed", "gvp", List("noaa" -> "x", "gvp" -> "9", "gvp" -> "10")), Some("gvp" -> "10"))
    assertEquals(CatalogRecord.owner("x", "gvp", List("noaa" -> "x")), None)

  test("an item read in two Wikidata classes keeps the more specific kind, whichever class is read last"):
    val order = List("naval_battle", "siege", "battle", "revolt", "war")
    val more = EventProviders.WikidataEvents.moreSpecific(order, _, _)
    assertEquals(more("naval_battle", "battle"), "naval_battle")
    assertEquals(more("battle", "naval_battle"), "naval_battle")
    assertEquals(more("war", "revolt"), "revolt")
    assertEquals(more("battle", "natural_disaster"), "battle", "a kind outside the classes yields")
    assertEquals(more("natural_disaster", "battle"), "battle")
