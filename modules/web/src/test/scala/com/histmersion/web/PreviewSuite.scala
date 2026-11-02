package com.histmersion.web

import com.histmersion.api.views.PlaceDetail
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.library.Source

class PreviewSuite extends munit.FunSuite:

  private val detail = com.histmersion.api.library.Source.Detail(
    "cline2014_year",
    "1177 BC",
    List("Eric H. Cline"),
    Some(2014),
    Some("Princeton UP"),
    Source.Status.Current,
    List("bronze_age_collapse"),
    List("Eastern_Mediterranean"),
    List("sea_peoples"),
    Some(-1250),
    Some(-1100),
    Some("x" * 400),
    Some("10.1515/9781400849987"),
    None,
    None,
    None,
    None
  )

  test("a data-ck element previews a source, a data-slug element an article; a source wins"):
    assertEquals(Preview.targetOf(Some("a2020_b"), None), Some(Preview.Target.Source("a2020_b")))
    assertEquals(Preview.targetOf(None, Some("sea_peoples")), Some(Preview.Target.Article("sea_peoples")))
    assertEquals(Preview.targetOf(Some("a2020_b"), Some("x")), Some(Preview.Target.Source("a2020_b")))
    assertEquals(Preview.targetOf(Some(" "), None), None)

  test("the source card shows the DOI as identifier, readable places and a bounded summary"):
    val c = Preview.card(detail)
    assertEquals(c.identifier, "10.1515/9781400849987")
    assertEquals(c.byline, "Eric H. Cline · 2014 · Princeton UP")
    assertEquals(c.where, "Eastern Mediterranean · 1250 BCE–1100 BCE")
    assertEquals(c.status, Some(Source.Status.Current))
    assert(c.summary.exists(s => s.length == 300 && s.endsWith("…")), c.summary)
    assertEquals(Preview.card(detail.copy(doi = None)).identifier, "cline2014_year")

  test("InPage reads only same-page fragments, and match stepping wraps"):
    assertEquals(InPage.fragmentOf("#cite-3"), Some("cite-3"))
    assertEquals(InPage.fragmentOf("/source/x#y"), None)
    assertEquals(InPage.fragmentOf("#"), None)
    assertEquals(InPage.nextIndex(-1, 3), 0)
    assertEquals(InPage.nextIndex(2, 3), 0)
    assertEquals(InPage.nextIndex(0, 0), -1)

  // ── a place's card says what the place is (2026-10-09) ─────────────────────────────────────────

  private val patones = PlaceDetail(
    com.histmersion.api.catalogue.Place.Summary(
      "wikidata_q131839713",
      "Kingdom of Patones",
      Place.Kind.Polity,
      None,
      Some(1769L),
      Some("Wikidata (wikidata.org), CC0"),
      "Kingdom of Patones, Reino de Patones",
      dating = com.histmersion.api.catalogue.Place.Dating.EndOnly,
      importSource = Some("wikidata")
    ),
    geometry = Some("""{"type":"Point","coordinates":[-3.48,40.88]}"""),
    parents = Nil,
    children = Nil,
    findings = 0,
    asOf = Some(-3900L),
    links =
      List(com.histmersion.api.catalogue.Place.Link("wikidata_q131839713", "wikidata", "Q131839713", "exact_match")),
    hasBoundary = false,
    descriptions = List(
      com.histmersion.api.catalogue.Place.Description("former country in Modern Day Spain", "en", "wikidata"),
      com.histmersion.api.catalogue.Place.Description("microestado de Europa (siglo VIII-1769)", "es", "wikidata")
    )
  )

  test("a place card: label, kind, the period as stated, the description, no boundary, the source and its page"):
    val c = Preview.card(patones)
    assertEquals(c.title, "Kingdom of Patones")
    assertEquals(c.byline, "Polity · end 1769 CE, start unknown")
    assertEquals(c.summary, Some("former country in Modern Day Spain · microestado de Europa (siglo VIII-1769) (es)"))
    assert(c.facts.contains(com.histmersion.api.catalogue.Place.Shape.NoBoundaryNote), c.facts.toString)
    assert(c.facts.contains("source: Wikidata"), c.facts.toString)
    assertEquals(c.links, List("Wikidata Q131839713" -> "https://www.wikidata.org/wiki/Q131839713"))

  test("a place card: an undated import says so; a site's point is no missing boundary; a seed place names its source"):
    val gn = patones.copy(
      place = patones.place.copy(
        id = "geonames_1",
        label = "Boğazkale",
        kind = Place.Kind.Site,
        periodEnd = None,
        dating = com.histmersion.api.catalogue.Place.Dating.Unknown,
        importSource = Some("geonames")
      ),
      links = List(com.histmersion.api.catalogue.Place.Link("geonames_1", "geonames", "1", "exact_match")),
      descriptions = Nil
    )
    val c = Preview.card(gn)
    assertEquals(c.byline, "Site · date unknown")
    assert(!c.facts.contains(com.histmersion.api.catalogue.Place.Shape.NoBoundaryNote))
    assertEquals(c.links, List("GeoNames 1" -> "https://www.geonames.org/1"))
    assertEquals(c.summary, None)
    val seed = gn.copy(
      place = gn.place.copy(
        importSource = None,
        dating = com.histmersion.api.catalogue.Place.Dating.Stated,
        kind = Place.Kind.Region,
        periodStart = Some(-1650L),
        periodEnd = Some(-1180L),
        source = Some("Pleiades (pleiades.stoa.org), CC-BY")
      ),
      hasBoundary = true,
      links = Nil
    )
    val s = Preview.card(seed)
    assertEquals(s.byline, "Region · 1650 BCE–1180 BCE")
    assert(s.facts.contains("source: Pleiades (pleiades.stoa.org), CC-BY"))
    assert(!s.facts.contains(com.histmersion.api.catalogue.Place.Shape.NoBoundaryNote))

  test("a place card at a time says when an undated place is there only by its findings"):
    val anatolia = patones.copy(
      place =
        com.histmersion.api.catalogue.Place.Summary("anatolia", "Anatolia", Place.Kind.Region, None, None, None, ""),
      links = Nil,
      descriptions = Nil,
      timeBasis = com.histmersion.api.catalogue.Place.TimeBasis.Findings
    )
    assertEquals(
      Preview.card(anatolia).facts.headOption,
      Some(com.histmersion.api.catalogue.Place.TimeBasis.FindingsNote)
    )
    // across all time nothing is filtered, so nothing needs saying; a dated place never says it
    assert(
      !Preview
        .card(anatolia.copy(asOf = None))
        .facts
        .contains(com.histmersion.api.catalogue.Place.TimeBasis.FindingsNote)
    )
    assert(!Preview.card(patones).facts.contains(com.histmersion.api.catalogue.Place.TimeBasis.FindingsNote))

  test("a place card: a grid-square point says it is approximate; a people is a group, not a missing boundary"):
    val thessalia = patones.copy(
      place = patones.place.copy(
        id = "pleiades_1001895",
        label = "Thessalia",
        kind = Place.Kind.Region,
        importSource = None,
        dating = com.histmersion.api.catalogue.Place.Dating.Stated,
        periodEnd = None
      ),
      links = Nil,
      descriptions = Nil,
      locationPrecision = Place.Precision.GridCell
    )
    val t = Preview.card(thessalia)
    assert(t.facts.contains("location: approximate (atlas grid square)"), t.facts.toString)
    assert(t.facts.contains(com.histmersion.api.catalogue.Place.Shape.NoBoundaryNote), t.facts.toString)
    val ubii = thessalia.copy(
      place = thessalia.place.copy(id = "pleiades_109400", label = "Ubii", kind = Place.Kind.People),
      locationPrecision = Place.Precision.Stated
    )
    val u = Preview.card(ubii)
    assertEquals(u.byline.takeWhile(_ != ' '), "People")
    assert(u.facts.contains(com.histmersion.api.catalogue.Place.Shape.PeopleNote), u.facts.toString)
    assert(!u.facts.contains(com.histmersion.api.catalogue.Place.Shape.NoBoundaryNote), u.facts.toString)
    assert(!u.facts.exists(_.contains("approximate")), u.facts.toString)
