package com.histmersion.plugins.events

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{CatalogRecord, EventPrecision}
import com.histmersion.plugin.*
import com.histmersion.pluginhost.PluginEvents
import com.histmersion.upstreams.dating.CatalogDating

import io.circe.Json
import io.circe.parser.parse

/** Histmersion's own event catalogues, from their answers RECORDED 2026-10-14 (trimmed): each read into the events the
  * SPI exchanges, and those as the host stores them (its check, its dating held inside the present and the catalogue's
  * floor); each stored record read again without the network, as `events rederive` reads it.
  */
class CataloguesSuite extends munit.FunSuite:

  private def fixture(name: String): Json =
    parse(scala.io.Source.fromResource(s"events/$name")(using scala.io.Codec.UTF8).mkString).fold(throw _, identity)

  private def years(e: CatalogueEvent) =
    (
      e.notBefore.map(_.value.toLong),
      e.beginBy.map(_.value.toLong),
      e.endFrom.map(_.value.toLong),
      e.notAfter.map(_.value.toLong)
    )

  private val gvpSettings = GvpEvents(-12000, GvpSettings("https://gvp.example/ows", 5000, 1000, 0, -9700))
  private val gvp = GvpCatalogue(gvpSettings)
  private val limits = PluginEvents.limits(Some(gvp), 2026)

  /** As the host stores an event of `provider`: checked, then dated inside `limits`. */
  private def stored(provider: String, e: CatalogueEvent, l: CatalogDating.Limits = limits): CatalogRecord =
    PluginEvents.derive(PluginEvents.record(provider, e).fold(fail(_), _.record), l)

  // ── GVP ─────────────────────────────────────────────────────────────────────────────────────

  test("GVP: eruptions with VEI, the volcano as group, uncertainty from the record"):
    val (recs, total) = GvpCatalogue.parse(fixture("gvp.json")).toOption.get
    assertEquals(total, Some(3))
    val thera = recs.find(_.id == "13879").get
    assertEquals(thera.label, "Eruption of Santorini")
    assertEquals(thera.kind, "eruption")
    assertEquals(thera.vei, Some(7))
    assertEquals(years(thera), (Some(-1624L), None, None, Some(-1596L)))
    assertEquals(thera.group, Some("volcano:212040"))
    assertEquals((thera.lat.isDefined, thera.lon.isDefined), (true, true))
    assertEquals(thera.url, "https://volcano.si.edu/volcano.cfm?vn=212040")
    // an eruption with an end year
    val r46 = recs.find(_.id == "13882").get
    assertEquals((r46.notBefore.map(_.value.toLong), r46.notAfter.map(_.value.toLong)), (Some(46L), Some(47L)))

  test("GVP: windows of years cover the range once, each a part"):
    assertEquals(GvpCatalogue.windows(-3000, -1, 1000), List((-3000L, -2001L), (-2000L, -1001L), (-1000L, -1L)))
    assertEquals(GvpCatalogue.windows(1, 0, 10), Nil)
    val url = GvpCatalogue.url("https://x/ows", -3000, -2001, 5000)
    assert(url.contains("CQL_FILTER=StartDateYear%3E%3D-3000+AND+StartDateYear%3C%3D-2001"), url)
    assertEquals(
      gvp.parts(CatalogueQuery(None, Year(-10000))),
      List("years:-12000..-11001", "years:-11000..-10001", "years:-10000..-10000")
    )
    assertEquals(gvp.floorYear, Some(Year(-9700)))

  /** A GVP WFS feature as the service answers (2026-10-16), `EndDateYear` null. */
  private def feature(num: Int, volcano: String, year: Long, pm: Long, method: String): Json =
    parse(
      s"""{"type":"Feature","id":"Smithsonian_VOTW_Holocene_Eruptions.fid-$num","geometry":{"type":"Point","coordinates":[-19.7,63.98]},
         |"properties":{"Volcano_Number":372070,"Volcano_Name":"$volcano","Eruption_Number":$num,"Activity_Type":"Confirmed Eruption",
         |"ExplosivityIndexMax":null,"StartEvidenceMethod":"$method","StartDateYearModifier":null,"StartDateYear":$year,
         |"StartDateYearUncertainty":$pm,"EndDateYear":null,"EndDateYearUncertainty":null}}""".stripMargin
    ).toOption.get

  private val samples = List(
    // eruption, volcano, year, ±, method, the span expected once held inside the present and the Holocene
    (12724, "Hekla", 250L, 2500L, "Tephrochronology", (-2251L, 2026L)),
    (12725, "Hekla", 350L, 1500L, "Tephrochronology", (-1151L, 1850L)),
    (12693, "Katla", -4950L, 2500L, "Tephrochronology", (-7450L, -2450L)),
    (13048, "La Palma", -4050L, 3000L, "Isotopic: K/Ar", (-7050L, -1050L)),
    (18283, "Hachijojima", -8020L, 2640L, "Tephrochronology", (-9700L, -5380L))
  )

  test(
    "GVP: the five records keep their year, ± and method; stored, the span is held inside the present and the Holocene"
  ):
    samples.foreach { case (num, volcano, year, pm, method, (lo, hi)) =>
      val r = stored("gvp", GvpCatalogue.record(feature(num, volcano, year, pm, method)).get)
      assertEquals((r.central, r.plusMinus, r.datingMethod), (Some(year), Some(pm), Some(method)), s"$num")
      assertEquals(r.timeSpan, TimeSpan(Some(lo), None, None, Some(hi)), s"$num: no certain core is invented")
      assertEquals(EventPrecision.of(r.timeSpan, r.plusMinus), EventPrecision.Imprecise, s"$num")
      assert(
        r.raw.isDefined && !r.description.exists(_.contains("dated by")),
        s"$num: the method is a field, not prose"
      )
    }

  test("GVP re-read: from its own record (the stored span not trusted, the names an import added kept)"):
    val parsed = GvpCatalogue.record(feature(12724, "Hekla", 250, 2500, "Tephrochronology")).get
    val again =
      gvp.reread(parsed.copy(notBefore = Some(Year(0)), notAfter = Some(Year(0)), names = List("Hekla tephra"))).get
    assertEquals(years(again), years(parsed))
    assert(again.names.contains("Hekla tephra") && again.names.contains("Hekla eruption"), again.names.toString)

  test(
    "GVP re-read: a record stored before V088 (no raw), its year and ± from its span, its method from its description"
  ):
    val parsed = GvpCatalogue.record(feature(12724, "Hekla", 250, 2500, "Tephrochronology")).get
    val legacy = parsed.copy(
      notBefore = Some(Year(-2251)),
      notAfter = Some(Year(2750)),
      central = None,
      plusMinus = None,
      datingMethod = None,
      raw = None,
      description = Some("Confirmed Eruption at Hekla; dated by Tephrochronology")
    )
    val r = PluginEvents.derive(PluginEvents.record("gvp", gvp.reread(legacy).get).fold(fail(_), _.record), limits)
    assertEquals((r.central, r.plusMinus, r.datingMethod), (Some(250L), Some(2500L), Some("Tephrochronology")))
    assertEquals(r.timeSpan, TimeSpan(Some(-2251), None, None, Some(2026)))
    assertEquals(r.description, Some("Confirmed Eruption at Hekla"))

  test("a stored record goes to its catalogue and back unchanged"):
    val (recs, _) = GvpCatalogue.parse(fixture("gvp.json")).toOption.get
    recs.foreach { e =>
      val r = PluginEvents.record("gvp", e).fold(fail(_), _.record)
      assertEquals(PluginEvents.event(r).flatMap(PluginEvents.record("gvp", _).toOption.map(_.record)), Some(r))
      assertEquals(PluginEvents.reread(gvp, "gvp", r), Some(r))
    }

  // ── NOAA ────────────────────────────────────────────────────────────────────────────────────

  private val noaa = NoaaCatalogue(
    NoaaSettings("https://noaa.example/api/v1", 200, 0, "earthquakes,volcanoes,bogus,tsunamis")
  )

  test("NOAA: earthquakes with magnitude, volcano events grouped by the GVP number, doubtful tsunamis contested"):
    val (eq, pages) = NoaaCatalogue.parse("earthquakes", fixture("noaa_earthquakes.json")).toOption.get
    assertEquals(pages, 50)
    assertEquals(eq.head.id, "earthquake:1")
    assertEquals(eq.head.magnitude, Some(7.3))
    assertEquals(eq.head.label, "Earthquake, Jordan: Bab-A-Daraa,Al-Karak")
    assertEquals(eq.head.notBefore.map(_.value.toLong), Some(-2150L))
    val (vs, _) = NoaaCatalogue.parse("volcanoes", fixture("noaa_volcanoes.json")).toOption.get
    val santorini = vs.find(_.id == "volcano:1351").get
    assertEquals(santorini.group, Some("volcano:212040"))
    assertEquals(santorini.vei, Some(7))
    val (ts, _) = NoaaCatalogue.parse("tsunamis", fixture("noaa_tsunamis_events.json")).toOption.get
    assertEquals(ts.find(_.id == "tsunami:1").map(_.historicity), Some("contested"), "validity 1: very doubtful")
    assertEquals(ts.find(_.id == "tsunami:3").map(_.historicity), Some("attested"), "validity 4: definite")
    assert(ts.forall(t => t.magnitude.isEmpty && t.vei.isEmpty), "a tsunami is not sized by its earthquake's magnitude")

  test("NOAA: a part per dataset, the next page named by the one before; every part read at every import"):
    assertEquals(noaa.parts(CatalogueQuery(None, Year(499))), List("earthquakes:1", "volcanoes:1", "tsunamis:1"))
    assert(noaa.everyPartEachImport)
    assertEquals(noaa.floorYear, None)
    val http = new Plugin.Http:
      def get(url: String, headers: Map[String, String]) = Right("""{"items":[],"totalPages":2}""".getBytes("UTF-8"))
    assertEquals(
      noaa.read("earthquakes:1", CatalogueQuery(None, Year(499)), http).map(_.next),
      Right(Some("earthquakes:2"))
    )
    assertEquals(noaa.read("earthquakes:2", CatalogueQuery(None, Year(499)), http).map(_.next), Right(None))

  test("NOAA and Wikidata re-read their records from their raw ones"):
    val (eq, _) = NoaaCatalogue.parse("earthquakes", fixture("noaa_earthquakes.json")).toOption.get
    val one = eq.head
    assertEquals(
      noaa.reread(one.copy(notBefore = None, notAfter = None, beginBy = None, endFrom = None)).map(years),
      Some(years(one))
    )
    val wd = WikidataCatalogue.parse(fixture("wikidata_siege.json"), "siege").toOption.get.head
    val wikidata = WikidataCatalogue(WikidataSettings("https://q.example/sparql", 0, "Q188055:siege", "en"))
    assertEquals(
      wikidata.reread(wd.copy(notBefore = None, notAfter = None, beginBy = None, endFrom = None)).map(years),
      Some(years(wd))
    )

  // ── Wikidata ────────────────────────────────────────────────────────────────────────────────

  test("Wikidata: one record per item, BCE years from astronomical ones, own coordinates before the location's"):
    val recs = WikidataCatalogue.parse(fixture("wikidata_siege.json"), "siege").toOption.get
    val alesia = recs.find(_.id == "Q131386").get
    assertEquals(alesia.label, "Battle of Alesia")
    assertEquals(alesia.wikidata.map(_.value: String), Some("Q131386"))
    assertEquals(
      (alesia.notBefore.map(_.value.toLong), alesia.notAfter.map(_.value.toLong)),
      (Some(-52L), Some(-52L)),
      "-0051 is 52 BCE"
    )
    assertEquals(alesia.lat.map(l => math.round(l.value)), Some(48L))
    assertEquals(alesia.historicity, "attested")
    assertEquals(recs.map(_.id).distinct.size, recs.size)

  test("Wikidata: an item that is a legendary battle or a mythical event is never attested"):
    val row = (legend: String) => parse(s"""{"results":{"bindings":[{
      "item":{"type":"uri","value":"http://www.wikidata.org/entity/Q1"},
      "itemLabel":{"type":"literal","value":"Fall of Troy"},
      "date":{"type":"literal","value":"-1183-01-01T00:00:00Z"},"datePrec":{"type":"literal","value":"9"},
      "coord":{"type":"literal","value":"Point(26.24 39.95)"},
      "legend":{"type":"uri","value":"http://www.wikidata.org/entity/$legend"}}]}}""").toOption.get
    assertEquals(WikidataCatalogue.parse(row("Q26913948"), "siege").toOption.get.head.historicity, "legendary")
    assertEquals(WikidataCatalogue.parse(row("Q24336466"), "siege").toOption.get.head.historicity, "mythic")

  test("Wikidata: a part per configured class, the query asks for the class and its subclasses"):
    val wikidata =
      WikidataCatalogue(WikidataSettings("https://q.example/sparql", 0, "Q178561:battle, bad, Q188055:siege", "de, fr"))
    assertEquals(wikidata.parts(CatalogueQuery(None, Year(499))), List("class:Q178561", "class:Q188055"))
    val q = WikidataCatalogue.sparql("Q188055", None, 499, "en")
    assert(q.contains("wdt:P31/wdt:P279* wd:Q188055") && q.contains("YEAR(?t) <= 499"), q)
    assert(
      WikidataCatalogue.sparql("Q1", Some(-3000), -1, "en").contains("YEAR(?t) <= 0 && YEAR(?t) >= -2999"),
      "historical years become astronomical"
    )
    assertEquals(WikidataSettings("u", 0, "", "de, fr").labelLanguages, "de,fr,en")

  test("each catalogue states the terms the host shows for its records"):
    val terms = List(gvp, noaa, WikidataCatalogue(WikidataSettings("u", 0, "", "en"))).map(c =>
      (c.title, c.licence.value: String)
    )
    assertEquals(
      terms,
      List(
        "Smithsonian Global Volcanism Program" -> "Smithsonian Terms of Use (non-commercial use with attribution)",
        "NOAA NCEI Natural Hazards" -> "public-domain",
        "Wikidata" -> "CC0-1.0"
      )
    )
