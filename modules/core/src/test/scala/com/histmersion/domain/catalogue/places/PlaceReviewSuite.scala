package com.histmersion
package domain.catalogue.places
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.*

import com.histmersion.domain.catalogue.places.PlaceReview.{Candidate, Evidence, LinkedItem, Proposal}
import com.histmersion.domain.catalogue.Place

/** What a Pleiades "region" really is (2026-10-09) — the survey's own examples. */
class PlaceReviewSuite extends munit.FunSuite:

  // ── what a point-only region is ──────────────────────────────────────────────────────────────

  private def ev(
      id: Evidence.Id,
      label: Evidence.Label,
      types: String,
      kind: Place.Kind.Wire = Place.Kind.Wire(Place.Kind.Region),
      start: Option[DeepTime.SignedYear] = None,
      end: Option[DeepTime.SignedYear] = None,
      items: List[LinkedItem] = Nil
  ) =
    Evidence(
      id,
      label,
      kind,
      types.split(",").toList.filter(_.nonEmpty).map(t => Evidence.FeatureTypes(t)),
      Nil,
      start,
      end,
      items
    )

  private def kindOf(p: Option[Proposal]): Option[Place.Kind.Wire] = p.collect { case r: Proposal.Reclassify => r.to }

  test("a people or a civic tribe is a people, not a territory"):
    for (id, label, types) <- List(
        (Evidence.Id("pleiades_330976685"), Evidence.Label("Aiantis"), "people"),
        (Evidence.Id("pleiades_908290183"), Evidence.Label("Hippothontis"), "people"),
        (Evidence.Id("pleiades_873367356"), Evidence.Label("tribus Scaptia"), "people"),
        (Evidence.Id("pleiades_109400"), Evidence.Label("Ubii"), "people"),
        (Evidence.Id("pleiades_197313"), Evidence.Label("Iapudes"), "label,people"),
        (Evidence.Id("pleiades_714971430"), Evidence.Label("Fidenates"), "unlocated,people"),
        (Evidence.Id("pleiades_580116"), Evidence.Label("Tetrakomoi"), "people")
      )
    do
      val p = PlaceReview.propose(ev(id, label, types))
      assertEquals(kindOf(p), Some(Place.Kind.People), s"$label: $p")
      assert(p.get.describe.value.startsWith(s"$id $label: region → people — a people or civic tribe"), p.get.describe)
      assertEquals(
        p.get.detail,
        p.get.describe.value.stripPrefix(s"$id "),
        "a lint item carries the id beside the line"
      )
      assert(p.get.describe.value.contains(s"Pleiades types: ${types.replace(",", ", ")}"), p.get.describe)

  test("a province or diocese is a polity, with its period where a source states one"):
    val vienn =
      PlaceReview.propose(ev(Evidence.Id("pleiades_992074"), Evidence.Label("Viennensis (diocese)"), "province"))
    assertEquals(kindOf(vienn), Some(Place.Kind.Polity))
    assert(vienn.get.describe.value.contains("no source states its period"), vienn.get.describe)
    // Pleiades dates Palaestina I to its late-antique period: kept, not rewritten
    val pal = PlaceReview.propose(
      ev(
        Evidence.Id("pleiades_1001939"),
        Evidence.Label("Palaestina I (late Roman province)"),
        "province",
        start = Some(DeepTime.SignedYear(300L)),
        end = Some(DeepTime.SignedYear(640L))
      )
    )
    assertEquals(
      pal,
      Some(
        Proposal.Reclassify(
          Evidence.Id("pleiades_1001939"),
          Evidence.Label("Palaestina I (late Roman province)"),
          Place.Kind.Wire(Place.Kind.Region),
          Place.Kind.Wire(Place.Kind.Polity),
          None,
          Problem.Unclassified(Legacy.Text("an administrative unit (a polity)")),
          List(Proposal.EvidenceLine("Pleiades types: province"))
        )
      )
    )
    // a linked Wikidata item dates an undated one
    val gs = PlaceReview.propose(
      ev(
        Evidence.Id("pleiades_1000"),
        Evidence.Label("Germania Superior"),
        "province",
        items = List(
          LinkedItem(
            LinkedItem.Ref("wikidata:Q153553"),
            Some(LinkedItem.ItemClass("Q1620908")),
            DeepTime.span(DeepTime.SignedYear(83L), DeepTime.SignedYear(297L))
          )
        )
      )
    )
    assertEquals(
      gs.collect { case r: Proposal.Reclassify => r.period }.flatten,
      DeepTime.span(DeepTime.SignedYear(83L), DeepTime.SignedYear(297L))
    )
    assert(gs.get.describe.value.contains("period 83 CE–297 CE"), gs.get.describe)
    assert(gs.get.describe.value.contains("wikidata:Q153553: historical region 83 CE–297 CE"), gs.get.describe)
    // Pleiades' "still extant" 2100 is no end for a palace district
    val pylos = PlaceReview.propose(
      ev(
        Evidence.Id("pleiades_226979725"),
        Evidence.Label("Hither Province (Pylos)"),
        "region,province-2",
        start = Some(DeepTime.SignedYear(-1600L)),
        end = Some(DeepTime.SignedYear(2100L))
      )
    )
    assertEquals(pylos.collect { case r: Proposal.Reclassify => (r.to, r.period) }, Some((Place.Kind.Polity, None)))
    assert(pylos.get.describe.value.contains("no source states its period"), pylos.get.describe)
    // the seed filed `province-2` alone as a site: still a province
    assertEquals(
      kindOf(
        PlaceReview.propose(
          ev(
            Evidence.Id("pleiades_991374"),
            Evidence.Label("Thessalia (late Roman province)"),
            "province-2",
            kind = Place.Kind.Wire(Place.Kind.Site)
          )
        )
      ),
      Some(Place.Kind.Polity)
    )
    // …but a provincial capital that is a settlement stays a site
    assertEquals(
      PlaceReview.propose(
        ev(
          Evidence.Id("pleiades_x"),
          Evidence.Label("Capital"),
          "settlement,province",
          kind = Place.Kind.Wire(Place.Kind.Site)
        )
      ),
      None
    )
    // a polity that already is one, dated as stated, is left alone
    assertEquals(
      PlaceReview.propose(
        ev(
          Evidence.Id("pleiades_1001939"),
          Evidence.Label("Palaestina I"),
          "province",
          kind = Place.Kind.Wire(Place.Kind.Polity),
          start = Some(DeepTime.SignedYear(300L)),
          end = Some(DeepTime.SignedYear(640L))
        )
      ),
      None
    )

  test("a deme or a settlement filed as a region is a site; a plain region stays; a feature is never touched"):
    val pir = PlaceReview.propose(
      ev(Evidence.Id("pleiades_580062"), Evidence.Label("Peiraieus/Piraeus"), "region,settlement,deme-attic")
    )
    assertEquals(kindOf(pir), Some(Place.Kind.Site))
    assert(pir.get.describe.value.contains("a settlement (deme-attic, settlement), not a region"), pir.get.describe)
    assertEquals(
      PlaceReview.propose(ev(Evidence.Id("pleiades_307963010"), Evidence.Label("Regio I (Porta Capena)"), "region")),
      None
    )
    assertEquals(
      PlaceReview.propose(ev(Evidence.Id("pleiades_570585"), Evidence.Label("Petras"), "region,label")),
      None
    )
    assertEquals(
      PlaceReview.propose(
        ev(
          Evidence.Id("pleiades_511405"),
          Evidence.Label("Salmydessos (coastal region)"),
          "region,label,settlement-modern,coast"
        )
      ),
      None,
      "the label says region"
    )
    assertEquals(
      PlaceReview.propose(
        ev(
          Evidence.Id("pleiades_109341"),
          Evidence.Label("Sequana (river)"),
          "river,people",
          kind = Place.Kind.Wire(Place.Kind.Feature)
        )
      ),
      None
    )

  test("a linked historical country is evidence for a person, not a fix (Wikidata files Brittany as one)"):
    val p = PlaceReview.propose(
      ev(
        Evidence.Id("curated_brittany"),
        Evidence.Label("Brittany"),
        "",
        items = List(LinkedItem(LinkedItem.Ref("wikidata:Q327"), Some(LinkedItem.ItemClass("Q3024240")), None))
      )
    )
    assert(p.exists(!_.fixable), p.toString)
    assert(p.get.describe.value.contains("historical country"), p.get.describe)
    assertEquals(
      PlaceReview.propose(
        ev(
          Evidence.Id("curated_y"),
          Evidence.Label("Y"),
          "",
          items = List(LinkedItem(LinkedItem.Ref("wikidata:Q2"), Some(LinkedItem.ItemClass("Q1620908")), None))
        )
      ),
      None
    )

  test("a withdrawn duplicate merges into the one seed place of its name; otherwise a person decides"):
    val dup = ev(Evidence.Id("pleiades_981515"), Evidence.Label("Withdrawn (duplicate): Creta et Cyrene"), "province")
    assertEquals(PlaceReview.duplicateOf(dup.label), Some("Creta et Cyrene"))
    assertEquals(
      PlaceReview.duplicateOf(Evidence.Label("Withdrawn duplicate: Moesia Superior")),
      Some("Moesia Superior")
    )
    assertEquals(PlaceReview.duplicateOf(Evidence.Label("Withdrawn: Untitled")), None)
    assertEquals(
      PlaceReview.duplicateKeys(Evidence.Label("Peiros/Piros (river)")),
      List("peiros piros", "peiros", "piros")
    )
    val into = Candidate(
      Evidence.Id("pleiades_981517"),
      Evidence.Label("Creta et Cyrene (Roman province)"),
      Place.Kind.Wire(Place.Kind.Polity)
    )
    val m = PlaceReview.propose(dup, List(into))
    assertEquals(m.map(_.getClass.getSimpleName), Some("Merge"))
    assertEquals(
      m.get.describe.value.takeWhile(_ != '—').trim,
      "pleiades_981515 Withdrawn (duplicate): Creta et Cyrene: merge into pleiades_981517 Creta et Cyrene (Roman province)"
    )
    assert(m.get.fixable)
    val none = PlaceReview.propose(dup, Nil).get
    assert(!none.fixable && none.describe.value.contains("no seed place has that name"), none.describe)
    val two = PlaceReview
      .propose(
        dup,
        List(
          into,
          Candidate(Evidence.Id("pleiades_2"), Evidence.Label("Creta et Cyrene"), Place.Kind.Wire(Place.Kind.Region))
        )
      )
      .get
    assert(!two.fixable && two.describe.value.contains("2 seed places have that name"), two.describe)
    val withdrawn = PlaceReview
      .propose(
        ev(
          Evidence.Id("pleiades_9"),
          Evidence.Label("Withdrawn: Untitled"),
          "",
          kind = Place.Kind.Wire(Place.Kind.Site)
        )
      )
      .get
    assert(!withdrawn.fixable && withdrawn.describe.value.contains("no replacement named"), withdrawn.describe)
