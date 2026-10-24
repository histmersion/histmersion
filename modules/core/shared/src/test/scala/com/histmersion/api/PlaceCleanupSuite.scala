package com.histmersion.api

import com.histmersion.domain.*
import com.histmersion.domain.given

import com.histmersion.api.PlaceReview.{Candidate, Evidence, LinkedItem, Proposal}

/** The point-only seed regions (2026-10-09): which points are only an atlas grid square's centre, and
  * what a Pleiades "region" really is — the survey's own examples. */
class PlaceCleanupSuite extends munit.FunSuite:

  test("grid centres: 5°, 1° and ½° cells, both coordinates; nothing else"):
    // the survey's points: Thessalia the province, Viennensis/Tarraconensis, Akkad
    assert(LocationPrecision.gridCentre(22.5, 37.5))
    assert(LocationPrecision.gridCentre(-2.5, 42.5))
    assert(LocationPrecision.gridCentre(44.5, 32.5), "1° cell")
    assert(LocationPrecision.gridCentre(33.75, 16.25), "½° cell")
    assert(LocationPrecision.gridCentre(-0.25, 46.75), "west of Greenwich")
    assert(!LocationPrecision.gridCentre(22.5, 37.0), "one coordinate only")
    assert(!LocationPrecision.gridCentre(6.0, 47.0), "a cell corner is not its centre")
    assert(!LocationPrecision.gridCentre(22.29543, 39.44387), "Thessalia the region, a real point")
    assert(LocationPrecision.gridCentre(22.5000001, 37.5), "a micro-degree is the resolution")
    assert(!LocationPrecision.gridCentre(22.50001, 37.5))

  test("only a Pleiades point is an atlas grid square; a Wikidata coordinate in whole minutes is not"):
    assertEquals(LocationPrecision.of("pleiades_1001895", 22.5, 37.5), LocationPrecision.GridCell)
    assertEquals(LocationPrecision.of("curated_brittany", 22.5, 37.5), LocationPrecision.Stated)
    assertEquals(LocationPrecision.of("wikidata_q1", 22.25, 37.25), LocationPrecision.Stated)
    assertEquals(LocationPrecision.of("pleiades_1332", 22.29543, 39.44387), LocationPrecision.Stated)
    assertEquals(LocationPrecision.values.toList.map(_.wire), List("stated", "grid-cell"))
    assertEquals(LocationPrecision.parse("grid-cell"), Some(LocationPrecision.GridCell))

  // ── what a point-only region is ──────────────────────────────────────────────────────────────

  private def ev(id: String, label: String, types: String, kind: String = PlaceKind.Region,
      start: Option[Long] = None, end: Option[Long] = None, items: List[LinkedItem] = Nil) =
    Evidence(id, label, kind, types.split(",").toList.filter(_.nonEmpty), Nil, start, end, items)

  private def kindOf(p: Option[Proposal]): Option[String] = p.collect { case r: Proposal.Reclassify => r.to }

  test("a people or a civic tribe is a people, not a territory"):
    for (id, label, types) <- List(
        ("pleiades_330976685", "Aiantis", "people"), ("pleiades_908290183", "Hippothontis", "people"),
        ("pleiades_873367356", "tribus Scaptia", "people"), ("pleiades_109400", "Ubii", "people"),
        ("pleiades_197313", "Iapudes", "label,people"), ("pleiades_714971430", "Fidenates", "unlocated,people"),
        ("pleiades_580116", "Tetrakomoi", "people"))
    do
      val p = PlaceReview.propose(ev(id, label, types))
      assertEquals(kindOf(p), Some(PlaceKind.People), s"$label: $p")
      assert(p.get.describe.startsWith(s"$id $label: region → people — a people or civic tribe"), p.get.describe)
      assertEquals(p.get.detail, p.get.describe.stripPrefix(s"$id "), "a lint item carries the id beside the line")
      assert(p.get.describe.contains(s"Pleiades types: ${types.replace(",", ", ")}"), p.get.describe)

  test("a province or diocese is a polity, with its period where a source states one"):
    val vienn = PlaceReview.propose(ev("pleiades_992074", "Viennensis (diocese)", "province"))
    assertEquals(kindOf(vienn), Some(PlaceKind.Polity))
    assert(vienn.get.describe.contains("no source states its period"), vienn.get.describe)
    // Pleiades dates Palaestina I to its late-antique period: kept, not rewritten
    val pal = PlaceReview.propose(ev("pleiades_1001939", "Palaestina I (late Roman province)", "province",
      start = Some(300L), end = Some(640L)))
    assertEquals(pal, Some(Proposal.Reclassify("pleiades_1001939", "Palaestina I (late Roman province)",
      PlaceKind.Region, PlaceKind.Polity, None, "an administrative unit (a polity)", List("Pleiades types: province"))))
    // a linked Wikidata item dates an undated one
    val gs = PlaceReview.propose(ev("pleiades_1000", "Germania Superior", "province",
      items = List(LinkedItem("wikidata:Q153553", Some("Q1620908"), Some(Period(83, 297))))))
    assertEquals(gs.collect { case r: Proposal.Reclassify => r.period }.flatten, Some(Period(83, 297)))
    assert(gs.get.describe.contains("period 83 CE–297 CE"), gs.get.describe)
    assert(gs.get.describe.contains("wikidata:Q153553: historical region 83 CE–297 CE"), gs.get.describe)
    // Pleiades' "still extant" 2100 is no end for a palace district
    val pylos = PlaceReview.propose(ev("pleiades_226979725", "Hither Province (Pylos)", "region,province-2",
      start = Some(-1600L), end = Some(2100L)))
    assertEquals(pylos.collect { case r: Proposal.Reclassify => (r.to, r.period) }, Some((PlaceKind.Polity, None)))
    assert(pylos.get.describe.contains("no source states its period"), pylos.get.describe)
    // the seed filed `province-2` alone as a site: still a province
    assertEquals(kindOf(PlaceReview.propose(ev("pleiades_991374", "Thessalia (late Roman province)", "province-2",
      kind = PlaceKind.Site))), Some(PlaceKind.Polity))
    // …but a provincial capital that is a settlement stays a site
    assertEquals(PlaceReview.propose(ev("pleiades_x", "Capital", "settlement,province", kind = PlaceKind.Site)), None)
    // a polity that already is one, dated as stated, is left alone
    assertEquals(PlaceReview.propose(ev("pleiades_1001939", "Palaestina I", "province", kind = PlaceKind.Polity,
      start = Some(300L), end = Some(640L))), None)

  test("a deme or a settlement filed as a region is a site; a plain region stays; a feature is never touched"):
    val pir = PlaceReview.propose(ev("pleiades_580062", "Peiraieus/Piraeus", "region,settlement,deme-attic"))
    assertEquals(kindOf(pir), Some(PlaceKind.Site))
    assert(pir.get.describe.contains("a settlement (deme-attic, settlement), not a region"), pir.get.describe)
    assertEquals(PlaceReview.propose(ev("pleiades_307963010", "Regio I (Porta Capena)", "region")), None)
    assertEquals(PlaceReview.propose(ev("pleiades_570585", "Petras", "region,label")), None)
    assertEquals(PlaceReview.propose(ev("pleiades_511405", "Salmydessos (coastal region)", "region,label,settlement-modern,coast")),
      None, "the label says region")
    assertEquals(PlaceReview.propose(ev("pleiades_109341", "Sequana (river)", "river,people", kind = PlaceKind.Feature)), None)

  test("a linked historical country is evidence for a person, not a fix (Wikidata files Brittany as one)"):
    val p = PlaceReview.propose(ev("curated_brittany", "Brittany", "", items = List(LinkedItem("wikidata:Q327", Some("Q3024240"), None))))
    assert(p.exists(!_.fixable), p.toString)
    assert(p.get.describe.contains("historical country"), p.get.describe)
    assertEquals(PlaceReview.propose(ev("curated_y", "Y", "", items = List(LinkedItem("wikidata:Q2", Some("Q1620908"), None)))), None)

  test("a withdrawn duplicate merges into the one seed place of its name; otherwise a person decides"):
    val dup = ev("pleiades_981515", "Withdrawn (duplicate): Creta et Cyrene", "province")
    assertEquals(PlaceReview.duplicateOf(dup.label), Some("Creta et Cyrene"))
    assertEquals(PlaceReview.duplicateOf("Withdrawn duplicate: Moesia Superior"), Some("Moesia Superior"))
    assertEquals(PlaceReview.duplicateOf("Withdrawn: Untitled"), None)
    assertEquals(PlaceReview.duplicateKeys("Peiros/Piros (river)"), List("peiros piros", "peiros", "piros"))
    val into = Candidate("pleiades_981517", "Creta et Cyrene (Roman province)", PlaceKind.Polity)
    val m = PlaceReview.propose(dup, List(into))
    assertEquals(m.map(_.getClass.getSimpleName), Some("Merge"))
    assertEquals(m.get.describe.takeWhile(_ != '—').trim,
      "pleiades_981515 Withdrawn (duplicate): Creta et Cyrene: merge into pleiades_981517 Creta et Cyrene (Roman province)")
    assert(m.get.fixable)
    val none = PlaceReview.propose(dup, Nil).get
    assert(!none.fixable && none.describe.contains("no seed place has that name"), none.describe)
    val two = PlaceReview.propose(dup, List(into, Candidate("pleiades_2", "Creta et Cyrene", PlaceKind.Region))).get
    assert(!two.fixable && two.describe.contains("2 seed places have that name"), two.describe)
    val withdrawn = PlaceReview.propose(ev("pleiades_9", "Withdrawn: Untitled", "", kind = PlaceKind.Site)).get
    assert(!withdrawn.fixable && withdrawn.describe.contains("no replacement named"), withdrawn.describe)

  test("the people kind: offered, parsed from foreign labels, and no area"):
    assert(PlaceKind.all.contains(PlaceKind.People))
    assertEquals(PlaceKind.parse("people, tribe"), Some(PlaceKind.People))
    assertEquals(PlaceKind.parse("tribe"), Some(PlaceKind.People))
    assert(!PlaceShape.isArea(PlaceKind.People))
    assert(!PlaceShape.pointOnlyArea(PlaceKind.People, hasBoundary = false))

  test("a place's Markdown: the shape and, for a grid-square point, the location"):
    val pt = Some("""{"type":"Point","coordinates":[22.5,37.5]}""")
    def detail(kind: String, precision: LocationPrecision) = PlaceDetail(
      PlaceSummary("pleiades_1001895", "Thessalia", kind, None, None, None, ""), pt, Nil, Nil, 0, None,
      locationPrecision = precision)
    val thess = Markdown.place(detail(PlaceKind.Polity, LocationPrecision.GridCell))
    assert(thess.contains(s"**Shape:** ${PlaceShape.NoBoundaryNote}"), thess)
    assert(thess.contains("**Location:** approximate (atlas grid square)"), thess)
    assertEquals(detail(PlaceKind.Polity, LocationPrecision.GridCell).shapeNotes,
      List(PlaceShape.NoBoundaryNote, "location: approximate (atlas grid square)"))
    val ubii = Markdown.place(detail(PlaceKind.People, LocationPrecision.Stated))
    assert(ubii.contains(s"**Shape:** ${PlaceShape.PeopleNote}") && !ubii.contains("**Location:**"), ubii)
