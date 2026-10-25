package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given


import io.circe.Json
import io.circe.parser.parse

/** #1449, the pure half: the kind taxonomy, the datings (BCE, uncertainty, Wikidata precision), the
  * three catalogues' answers read from RECORDED fixtures (captured 2026-10-14, trimmed), the thresholds,
  * and the merge of records into one event per thing. */
class EventCatalogSuite extends munit.FunSuite:

  private def fixture(name: String): Json =
    parse(scala.io.Source.fromResource(s"events/$name")(using scala.io.Codec.UTF8).mkString).fold(throw _, identity)

  // ── taxonomy ────────────────────────────────────────────────────────────────────────────────

  test("every kind of the first fifty events is in the taxonomy, with a group and an icon"):
    val first = List("destruction", "migration", "technological_change", "polity_formation", "natural_disaster",
      "battle", "war", "cultural_shift", "collapse", "diplomatic_era", "invasion")
    first.foreach { k =>
      val e = EventTaxonomy.of(k)
      assert(EventTaxonomy.kinds.exists(_.kind == e.kind), s"$k → ${e.kind} is not a kind of the table")
      assert(e.icon.nonEmpty)
    }
    assertEquals(EventTaxonomy.canonical("diplomatic_era"), EventTaxonomy.Era)
    assertEquals(EventTaxonomy.canonical("Naval battle"), EventTaxonomy.NavalBattle)
    assertEquals(EventTaxonomy.canonical("Rebellion"), EventTaxonomy.Revolt)
    assertEquals(EventTaxonomy.group("eruption"), EventTaxonomy.Natural)
    assertEquals(EventTaxonomy.group("siege"), EventTaxonomy.Human)
    assertEquals(EventTaxonomy.group("coronation"), EventTaxonomy.Human, "an unknown kind is human, generic icon")
    assertEquals(EventTaxonomy.icon("coronation"), "circle-dot")

  test("a natural_disaster becomes the sub-kind its label names, else stays generic"):
    assertEquals(EventTaxonomy.refine("natural_disaster", "Minoan eruption of Thera"), "eruption")
    assertEquals(EventTaxonomy.refine("natural_disaster", "4.2 kiloyear climate event"), "climate_event")
    assertEquals(EventTaxonomy.refine("natural_disaster", "Severe drought coincident with Hittite collapse"), "drought")
    assertEquals(EventTaxonomy.refine("natural_disaster", "Late Bronze Age environmental crisis"), "natural_disaster")
    assertEquals(EventTaxonomy.refine("battle", "Battle near a volcano"), "battle", "only the generic kind is refined")

  test("a kind filter: a group selects its kinds (human = every non-natural one), a kind its aliases"):
    val nat = EventTaxonomy.condition(Some("natural")).get
    assert(!nat.negate && nat.kinds.contains("eruption") && nat.kinds.contains("plague"))
    val hum = EventTaxonomy.condition(Some("human")).get
    assert(hum.negate && hum.kinds == nat.kinds)
    assertEquals(EventTaxonomy.condition(Some("revolt")).get.kinds.toSet, Set("revolt", "rebellion", "uprising"))
    assertEquals(EventTaxonomy.condition(Some(" ")), None)
    assert(EventTaxonomy.matches("natural", "tsunami") && !EventTaxonomy.matches("human", "tsunami"))

  // ── datings ─────────────────────────────────────────────────────────────────────────────────

  test("years: no year zero across the BCE/CE boundary"):
    assertEquals(Years.shift(-10, 30), 21L)
    assertEquals(Years.shift(5, -10), -6L)
    assertEquals(Years.fromAstronomical(-51), -52L)
    assertEquals(Years.fromAstronomical(0), -1L)
    assertEquals(Years.toAstronomical(-52), -51L)

  test("an uncertainty widens the outer bounds and never invents a certain core"):
    val s = CatalogDating.span(Some(CatalogDating.year(-1610, Some(14))), None)
    assertEquals(s, TimeSpan(Some(-1624), None, None, Some(-1596)))
    val exact = CatalogDating.span(Some(CatalogDating.year(79)), None)
    assertEquals(exact, TimeSpan(Some(79), Some(79), Some(79), Some(79)))
    val long = CatalogDating.span(Some(CatalogDating.year(-500, Some(10))), Some(CatalogDating.year(-450, Some(10))))
    assertEquals(long, TimeSpan(Some(-510), Some(-490), Some(-460), Some(-440)))

  test("Wikidata: an astronomical ISO year and its precision"):
    assertEquals(CatalogDating.isoYear("-0051-07-01T00:00:00Z"), Some(-51L))
    assertEquals(CatalogDating.isoYear("+0410-08-24T00:00:00Z"), Some(410L))
    assertEquals(CatalogDating.wikidata(-51, 9), CatalogDating.Bound(-52, -52))
    assertEquals(CatalogDating.wikidata(-51, 11), CatalogDating.Bound(-52, -52), "a day is its year")
    // a decade: the one the year is in (-60…-51 astronomical), widened by one decade each way
    assertEquals(CatalogDating.wikidata(-51, 8), CatalogDating.Bound(Years.fromAstronomical(-70), Years.fromAstronomical(-41)))
    val c = CatalogDating.wikidata(-450, 7)
    assert(c.lo <= -551 && c.hi >= -450, c.toString)

  // ── catalogues ──────────────────────────────────────────────────────────────────────────────

  test("GVP: eruptions with VEI, the volcano as group, uncertainty from the record"):
    val (recs, total) = EventProviders.Gvp.parse(fixture("gvp.json")).toOption.get
    assertEquals(total, Some(3))
    val thera = recs.find(_.providerId == "13879").get
    assertEquals(thera.label, "Eruption of Santorini")
    assertEquals(thera.kind, EventTaxonomy.Eruption)
    assertEquals(thera.vei, Some(7))
    assertEquals(thera.timeSpan, TimeSpan(Some(-1624), None, None, Some(-1596)))
    assertEquals(thera.group, Some("volcano:212040"))
    assertEquals((thera.lat.isDefined, thera.lon.isDefined), (true, true))
    assertEquals(thera.url, "https://volcano.si.edu/volcano.cfm?vn=212040")
    // an eruption with an end year
    val r46 = recs.find(_.providerId == "13882").get
    assertEquals(EventGeo.outer(r46.timeSpan), Some((46L, 47L)))

  test("GVP: windows of years cover the range once"):
    assertEquals(EventProviders.Gvp.windows(-3000, -1, 1000), List((-3000L, -2001L), (-2000L, -1001L), (-1000L, -1L)))
    assertEquals(EventProviders.Gvp.windows(1, 0, 10), Nil)
    val url = EventProviders.Gvp.url("https://x/ows", -3000, -2001, 5000)
    assert(url.contains("CQL_FILTER=StartDateYear%3E%3D-3000+AND+StartDateYear%3C%3D-2001"), url)

  test("NOAA: earthquakes with magnitude, volcano events grouped by the GVP number, doubtful tsunamis contested"):
    val (eq, pages) = EventProviders.Noaa.parse("earthquakes", fixture("noaa_earthquakes.json")).toOption.get
    assertEquals(pages, 50)
    assertEquals(eq.head.providerId, "earthquake:1")
    assertEquals(eq.head.magnitude, Some(7.3))
    assertEquals(eq.head.label, "Earthquake, Jordan: Bab-A-Daraa,Al-Karak")
    assertEquals(eq.head.timeSpan.notBefore, Some(-2150L))
    val (vs, _) = EventProviders.Noaa.parse("volcanoes", fixture("noaa_volcanoes.json")).toOption.get
    val santorini = vs.find(_.providerId == "volcano:1351").get
    assertEquals(santorini.group, Some("volcano:212040"))
    assertEquals(santorini.vei, Some(7))
    val (ts, _) = EventProviders.Noaa.parse("tsunamis", fixture("noaa_tsunamis_events.json")).toOption.get
    assertEquals(ts.find(_.providerId == "tsunami:1").map(_.historicity), Some(Historicity.Contested), "validity 1: very doubtful")
    assertEquals(ts.find(_.providerId == "tsunami:3").map(_.historicity), Some(Historicity.Attested), "validity 4: definite")
    assert(ts.forall(t => t.magnitude.isEmpty && t.vei.isEmpty), "a tsunami is not sized by its earthquake's magnitude")

  test("Wikidata: one record per item, BCE years from astronomical ones, own coordinates before the location's"):
    val recs = EventProviders.WikidataEvents.parse(fixture("wikidata_siege.json"), "siege").toOption.get
    val alesia = recs.find(_.providerId == "Q131386").get
    assertEquals(alesia.label, "Battle of Alesia")
    assertEquals(alesia.wikidata, Some("Q131386"))
    assertEquals(EventGeo.outer(alesia.timeSpan), Some((-52L, -52L)), "-0051 is 52 BCE")
    assertEquals(alesia.lat.map(math.round(_)), Some(48L))
    assertEquals(alesia.historicity, Historicity.Attested)
    assertEquals(recs.map(_.providerId).distinct.size, recs.size)

  test("Wikidata: an item that is a legendary battle or a mythical event is never attested"):
    val row = (legend: String) => parse(s"""{"results":{"bindings":[{
      "item":{"type":"uri","value":"http://www.wikidata.org/entity/Q1"},
      "itemLabel":{"type":"literal","value":"Fall of Troy"},
      "date":{"type":"literal","value":"-1183-01-01T00:00:00Z"},"datePrec":{"type":"literal","value":"9"},
      "coord":{"type":"literal","value":"Point(26.24 39.95)"},
      "legend":{"type":"uri","value":"http://www.wikidata.org/entity/$legend"}}]}}""").toOption.get
    assertEquals(EventProviders.WikidataEvents.parse(row("Q26913948"), "siege").toOption.get.head.historicity, Historicity.Legendary)
    assertEquals(EventProviders.WikidataEvents.parse(row("Q24336466"), "siege").toOption.get.head.historicity, Historicity.Mythic)

  test("Wikidata: the class table from config, the query asks for the class and its subclasses"):
    assertEquals(EventProviders.WikidataEvents.classes("Q178561:battle, bad, Q188055:Siege,Q178561:war"),
      List("Q178561" -> "battle", "Q188055" -> "siege"))
    val q = EventProviders.WikidataEvents.sparql("Q188055", None, 499, "en")
    assert(q.contains("wdt:P31/wdt:P279* wd:Q188055") && q.contains("YEAR(?t) <= 499"), q)
    assert(EventProviders.WikidataEvents.sparql("Q1", Some(-3000), -1, "en").contains("YEAR(?t) <= 0 && YEAR(?t) >= -2999"),
      "historical years become astronomical")

  test("thresholds: all inside the region, large ones outside, nothing without a position"):
    val region = BBox.parse("-25,20,90,70")
    val t = EventProviders.Thresholds(region, 0, 4, 0, 6.5)
    def erup(lat: Double, lon: Double, vei: Option[Int]) =
      CatalogRecord("gvp", "1", "", "x", "eruption", TimeSpan(Some(-1), None, None, Some(-1)), Some(lat), Some(lon), vei = vei)
    assert(EventProviders.keep(erup(37, 25, Some(1)), t).isRight)
    assert(EventProviders.keep(erup(-10, 120, Some(3)), t).isLeft)
    assert(EventProviders.keep(erup(-10, 120, Some(4)), t).isRight)
    assert(EventProviders.keep(erup(-10, 120, None), t).isLeft)
    assertEquals(EventProviders.keep(erup(37, 25, None).copy(lat = None), t), Left("no position"))
    assertEquals(EventProviders.keep(erup(37, 25, Some(5)), t.copy(area = BBox.parse("0,0,10,10"))), Left("outside --bbox"))

  // ── merging ─────────────────────────────────────────────────────────────────────────────────

  private val tol = CatalogMerge.Tolerance(60, 30)
  private def rec(p: String, id: String, kind: String, y: Long, lat: Double, lon: Double, wd: Option[String] = None,
      group: Option[String] = None) =
    CatalogRecord(p, id, "", s"$p $id", kind, TimeSpan(Some(y), Some(y), Some(y), Some(y)), Some(lat), Some(lon),
      wikidata = wd, group = group)

  test("merge: one catalogue's two records are two events; another catalogue's record of the same thing joins"):
    val a = rec("gvp", "1", "eruption", -1610, 36.4, 25.4, group = Some("volcano:212040"))
    val b = rec("gvp", "2", "eruption", -1600, 36.4, 25.4, group = Some("volcano:212040"))
    val c = rec("noaa", "volcano:1351", "eruption", -1610, 36.404, 25.396, group = Some("volcano:212040"))
    val placed = CatalogMerge.place(List(a, b, c), Nil, tol)
    assertEquals(placed.map(_._2.eventId), List("gvp_1", "gvp_2", "gvp_1"))
    assert(placed(2)._2.isInstanceOf[CatalogMerge.Placement.New])

  test("merge: a stored event is matched by key, by Wikidata item, or by kind+time+space — never by another kind"):
    val compiled = StoredEvent("thera_eruption", "eruption", TimeSpan(Some(-1620), None, None, Some(-1600)),
      Some(36.4), Some(25.4), None, Set.empty, catalogue = false)
    val battle = StoredEvent("alesia", "battle", TimeSpan(Some(-52), None, None, Some(-52)), Some(47.5), Some(4.5),
      Some("Q131386"), Set.empty, catalogue = false)
    val known = StoredEvent("gvp_9", "eruption", TimeSpan(Some(-79), None, None, Some(-79)), Some(40.8), Some(14.4),
      None, Set("gvp" -> "9"), catalogue = true)
    val stored = List(compiled, battle, known)
    val placed = CatalogMerge.place(List(
      rec("gvp", "1", "eruption", -1610, 36.41, 25.39),
      rec("wikidata", "Q131386", "siege", -52, 10, 10, wd = Some("Q131386")),
      rec("gvp", "9", "eruption", -79, 0, 0),
      rec("noaa", "earthquake:5", "earthquake", -1610, 36.4, 25.4)), stored, tol)
    assertEquals(placed.map(_._2.eventId), List("thera_eruption", "alesia", "gvp_9", "noaa_earthquake_5"))
    assert(placed.take(3).forall(_._2.isInstanceOf[CatalogMerge.Placement.Existing]))

  test("merge: a stored event that a record of the SAME catalogue made is no match for another of its records"):
    val g1 = StoredEvent("gvp_1", "eruption", TimeSpan(Some(-1610), None, None, Some(-1610)), Some(36.4), Some(25.4),
      None, Set("gvp" -> "1"), catalogue = true)
    val placed = CatalogMerge.place(List(rec("gvp", "2", "eruption", -1605, 36.4, 25.4)), List(g1), tol)
    assertEquals(placed.head._2.eventId, "gvp_2")

  test("event ids from records are stable and safe"):
    assertEquals(CatalogRecord.eventId(rec("noaa", "volcano:1351", "eruption", 0, 0, 0)), "noaa_volcano_1351")
    assertEquals(CatalogRecord.eventId(rec("wikidata", "Q131386", "battle", 0, 0, 0)), "wikidata_q131386")

  test("an import report adds up and reads as one line"):
    val a = EventImportReport("gvp", dryRun = true, records = 10, kept = 4, created = 3, merged = 1,
      byKind = List(EventCount("eruption", 4)), skipped = List(EventCount("no position", 6)), parts = 1)
    val b = a.copy(records = 5, kept = 5, created = 5, merged = 0, byKind = List(EventCount("eruption", 5)), skipped = Nil)
    val c = EventImportReport.combine(a, b)
    assertEquals((c.records, c.kept, c.created, c.merged, c.parts), (15, 9, 8, 1, 2))
    assertEquals(c.byKind, List(EventCount("eruption", 9)))
    assert(EventImportReport.summary(c).contains("would create 8 event(s)"))
    assert(EventImportReport.markdown(c).contains("dry run: nothing stored"))
    assertEquals(CatalogProvider.parse(" Smithsonian "), Right("gvp"))
    assert(CatalogProvider.parse("usgs").isLeft)
