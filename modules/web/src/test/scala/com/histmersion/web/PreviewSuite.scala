package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.views.PlaceDetail
import com.histmersion.api.catalogue.{Place, Year}
import com.histmersion.api.library.Source

class PreviewSuite extends munit.FunSuite:

  private def has(facts: List[Preview.Card.Facts], line: String): Boolean = facts.exists(_.value == line)

  private val detail = com.histmersion.api.library.Source.Detail(
    com.histmersion.api.library.Citekey("cline2014_year"),
    com.histmersion.api.library.Title("1177 BC"),
    (List("Eric H. Cline")).map(com.histmersion.api.library.AuthorName.apply),
    (Some(2014)).map(com.histmersion.api.catalogue.Year.apply),
    (Some("Princeton UP")).map(com.histmersion.api.library.Source.Venue.apply),
    Source.Status.Current,
    List(com.histmersion.api.library.Source.Topics("bronze_age_collapse")),
    List(com.histmersion.api.library.Source.Regions("Eastern_Mediterranean")),
    List(com.histmersion.api.library.Source.Tags("sea_peoples")),
    Some(com.histmersion.api.catalogue.Year(-1250)),
    Some(com.histmersion.api.catalogue.Year(-1100)),
    Some(Source.Summary("x" * 400)),
    (Some("10.1515/9781400849987")).map(com.histmersion.api.library.Doi.apply),
    None,
    None,
    None,
    None
  )

  test("a data-ck element previews a source, a data-slug element an article; a source wins"):
    assertEquals(Preview.targetOf(Some(com.histmersion.api.library.Citekey("a2020_b")), None), Some(Preview.Target.Source(com.histmersion.web.Preview.Target.Source.Citekey("a2020_b"))))
    assertEquals(Preview.targetOf(None, Some(com.histmersion.api.research.Article.Slug("sea_peoples"))), Some(Preview.Target.Article(com.histmersion.web.Preview.Target.Article.Slug("sea_peoples"))))
    assertEquals(Preview.targetOf(Some(com.histmersion.api.library.Citekey("a2020_b")), Some(com.histmersion.api.research.Article.Slug("x"))), Some(Preview.Target.Source(com.histmersion.web.Preview.Target.Source.Citekey("a2020_b"))))
    assertEquals(Preview.targetOf(Some(com.histmersion.api.library.Citekey(" ")), None), None)

  test("the source card shows the DOI as identifier, readable places and a bounded summary"):
    val c = Preview.card(detail)
    assertEquals(c.identifier, "10.1515/9781400849987")
    assertEquals(c.byline, "Eric H. Cline · 2014 · Princeton UP")
    assertEquals(c.where, "Eastern Mediterranean · 1250 BCE–1100 BCE")
    assertEquals(c.status, Some(Source.Status.Current))
    assert(c.summary.exists(s => s.value.length == 300 && s.value.endsWith("…")), c.summary.map(_.value))
    assertEquals(Preview.card(detail.modify(_.doi).setTo(None)).identifier, "cline2014_year")

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
      com.histmersion.api.catalogue.Place.Id("wikidata_q131839713"),
      com.histmersion.api.catalogue.Place.Summary.Label("Kingdom of Patones"),
      Place.Summary.Kind(Place.Kind.Polity),
      None,
      Some(com.histmersion.api.catalogue.Year(1769L)),
      Some(com.histmersion.api.catalogue.Place.Source("Wikidata (wikidata.org), CC0")),
      com.histmersion.api.catalogue.Place.Summary.Names("Kingdom of Patones, Reino de Patones"),
      dating = com.histmersion.api.catalogue.Place.Dating.EndOnly,
      importSource = Some(Place.Summary.ImportSource("wikidata"))
    ),
    geometry = Some(Place.Geometry("""{"type":"Point","coordinates":[-3.48,40.88]}""")),
    parents = Nil,
    children = Nil,
    findings = com.histmersion.api.catalogue.Place.Counts.Findings(0),
    asOf = Some(com.histmersion.api.catalogue.Year(-3900L)),
    links =
      List(com.histmersion.api.catalogue.Place.Link(com.histmersion.api.catalogue.Place.Id("wikidata_q131839713"), Place.Link.Scheme("wikidata"), Place.Link.ExternalId("Q131839713"), Place.Link.Relation("exact_match"))),
    hasBoundary = Place.Entry.HasBoundary(false),
    descriptions = List(
      com.histmersion.api.catalogue.Place.Description(Place.Description.Text("former country in Modern Day Spain"), Place.Description.Lang("en"), Place.Description.Source("wikidata")),
      com.histmersion.api.catalogue.Place.Description(Place.Description.Text("microestado de Europa (siglo VIII-1769)"), Place.Description.Lang("es"), Place.Description.Source("wikidata"))
    )
  )

  test("a place card: label, kind, the period as stated, the description, no boundary, the source and its page"):
    val c = Preview.card(patones)
    assertEquals(c.title, "Kingdom of Patones")
    assertEquals(c.byline, "Polity · end 1769 CE, start unknown")
    assertEquals(c.summary, Some("former country in Modern Day Spain · microestado de Europa (siglo VIII-1769) (es)"))
    assert(has(c.facts, Place.Shape.NoBoundaryNote.value), c.facts.toString)
    assert(has(c.facts, "source: Wikidata"), c.facts.toString)
    assertEquals(c.links, List("Wikidata Q131839713" -> "https://www.wikidata.org/wiki/Q131839713"))

  test("a place card: an undated import says so; a site's point is no missing boundary; a seed place names its source"):
    val gn = patones.copy(
      place = patones.place.modify(_.id).setTo(com.histmersion.api.catalogue.Place.Id("geonames_1")).modify(_.label).setTo(Place.Summary.Label("Boğazkale")).modify(_.kind).setTo(Place.Summary.Kind(Place.Kind.Site)).modify(_.periodEnd).setTo(None).modify(_.dating).setTo(com.histmersion.api.catalogue.Place.Dating.Unknown).modify(_.importSource).setTo(Some(Place.Summary.ImportSource("geonames"))
      ),
      links = List(com.histmersion.api.catalogue.Place.Link(com.histmersion.api.catalogue.Place.Id("geonames_1"), Place.Link.Scheme("geonames"), Place.Link.ExternalId("1"), Place.Link.Relation("exact_match"))),
      descriptions = Nil
    )
    val c = Preview.card(gn)
    assertEquals(c.byline, "Site · date unknown")
    assert(!has(c.facts, Place.Shape.NoBoundaryNote.value))
    assertEquals(c.links, List("GeoNames 1" -> "https://www.geonames.org/1"))
    assertEquals(c.summary, None)
    val seed = gn.copy(
      place = gn.place.modify(
        _.importSource).setTo(None).modify(_.dating).setTo(com.histmersion.api.catalogue.Place.Dating.Stated).modify(_.kind).setTo(Place.Summary.Kind(Place.Kind.Region)).modify(_.periodStart).setTo(Some(Year(-1650L))).modify(_.periodEnd).setTo(Some(Year(-1180L))).modify(_.source).setTo(Some(Place.Summary.Source("Pleiades (pleiades.stoa.org), CC-BY"))
      ),
      hasBoundary = Place.Entry.HasBoundary(true),
      links = Nil
    )
    val s = Preview.card(seed)
    assertEquals(s.byline, "Region · 1650 BCE–1180 BCE")
    assert(has(s.facts, "source: Pleiades (pleiades.stoa.org), CC-BY"))
    assert(!has(s.facts, Place.Shape.NoBoundaryNote.value))

  test("a place card at a time says when an undated place is there only by its findings"):
    val anatolia = patones.copy(
      place =
        com.histmersion.api.catalogue.Place.Summary(com.histmersion.api.catalogue.Place.Id("anatolia"), Place.Summary.Label("Anatolia"), Place.Summary.Kind(Place.Kind.Region), None, None, None, Place.Summary.Names("")),
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
        .card(anatolia.modify(_.asOf).setTo(None))
        .facts
        .exists(_.value == Place.TimeBasis.FindingsNote.value)
    )
    assert(!has(Preview.card(patones).facts, Place.TimeBasis.FindingsNote.value))

  test("a place card: a grid-square point says it is approximate; a people is a group, not a missing boundary"):
    val thessalia = patones.copy(
      place = patones.place.modify(_.id).setTo(com.histmersion.api.catalogue.Place.Id("pleiades_1001895")).modify(_.label).setTo(Place.Summary.Label("Thessalia")).modify(_.kind).setTo(Place.Summary.Kind(Place.Kind.Region)).modify(_.importSource).setTo(None).modify(_.dating).setTo(com.histmersion.api.catalogue.Place.Dating.Stated).modify(_.periodEnd).setTo(None
      ),
      links = Nil,
      descriptions = Nil,
      locationPrecision = Place.Precision.GridCell
    )
    val t = Preview.card(thessalia)
    assert(has(t.facts, "location: approximate (atlas grid square)"), t.facts.toString)
    assert(has(t.facts, Place.Shape.NoBoundaryNote.value), t.facts.toString)
    val ubii = thessalia.copy(
      place = thessalia.place.modify(_.id).setTo(com.histmersion.api.catalogue.Place.Id("pleiades_109400")).modify(_.label).setTo(Place.Summary.Label("Ubii")).modify(_.kind).setTo(Place.Summary.Kind(Place.Kind.People)),
      locationPrecision = Place.Precision.Stated
    )
    val u = Preview.card(ubii)
    assertEquals(u.byline.value.takeWhile(_ != ' '), "People")
    assert(has(u.facts, Place.Shape.PeopleNote.value), u.facts.toString)
    assert(!has(u.facts, Place.Shape.NoBoundaryNote.value), u.facts.toString)
    assert(!u.facts.exists(_.value.contains("approximate")), u.facts.toString)
