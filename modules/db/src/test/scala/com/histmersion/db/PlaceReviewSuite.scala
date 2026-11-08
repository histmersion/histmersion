package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.api.GeoPoint
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.catalogue.places.PlaceReview.Proposal

import com.augustnagro.magnum.*
import com.histmersion.api.library.{Finding, Source}
import com.histmersion.domain.catalogue.Place
import com.histmersion.util.database.given
import com.histmersion.util.identity.AliasTable
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year

/** `lint --rule place-kind` and `--rule place-grid-point` (2026-10-09), against a real PostGIS
  * (HISTMERSION_TEST_DB_URL; skipped otherwise): the dry run lists people, polities, a settlement and a merge and
  * writes nothing; `--fix` changes the kinds, merges the duplicate (links, alias) and matches findings again; a
  * grid-square place moves to its linked record's real point, once. Fixtures: [[RegionCleanupFixtures]].
  */
class PlaceReviewSuite extends RegionCleanupFixtures:

  private def mine(ps: List[Proposal]) =
    ps.filter(p => p.id.value.startsWith("pleiades_99910") || p.id.value.startsWith("zzrc"))

  test("place-kind: a dry run lists people, polities, a settlement and a merge, and changes nothing"):
    seed()
    val ps = mine(PlaceFixtures.proposals(ds))
    val byId = ps.map(p => (p.id.value: String) -> p).toMap
    assertEquals(byId.keySet, Set(Aiantis, Thess, Dup, Into, Peir))
    assertEquals(byId(Aiantis).asInstanceOf[Proposal.Reclassify].to, Place.Kind.People)
    assertEquals(byId(Thess).asInstanceOf[Proposal.Reclassify].to, Place.Kind.Polity)
    assertEquals(byId(Into).asInstanceOf[Proposal.Reclassify].to, Place.Kind.Polity, "province-2 filed as a site")
    assertEquals(byId(Peir).asInstanceOf[Proposal.Reclassify].to, Place.Kind.Site)
    assertEquals(byId(Dup).asInstanceOf[Proposal.Merge].into, Into)
    assert(byId(Aiantis).describe.value.startsWith(s"$Aiantis Zzrc Aiantis: region → people"), byId(Aiantis).describe)
    assertEquals(
      PlaceFixtures.get(ds, AiantisId).map(_.place.kind),
      Some(Place.Kind.Region),
      "a dry run writes nothing"
    )

  test("place-kind --fix: kinds change, the duplicate merges (links, alias), findings are matched again"):
    seed()
    connect(ds):
      val _ = sql"""INSERT INTO place_links (place_id, scheme, external_id, relation)
                    VALUES ($Dup, 'pleiades', '999100007', 'exact_match') ON CONFLICT DO NOTHING""".update.run()
    val (re, mg) =
      PlaceFixtures.applyProposals(ds, mine(PlaceFixtures.proposals(ds)), cfg.places.pointToleranceKm)
    assertEquals((re, mg), (4, 1))
    assertEquals(PlaceFixtures.get(ds, AiantisId).map(_.place.kind), Some(Place.Kind.People))
    assertEquals(PlaceFixtures.get(ds, PeirId).map(_.place.kind), Some(Place.Kind.Site))
    assertEquals(PlaceFixtures.get(ds, DupId), None)
    assertEquals(PlaceFixtures.renamedTo(ds, AliasTable.Id(Dup)), Some(Into), "the old id resolves to its replacement")
    val links = connect(ds)(
      sql"SELECT external_id FROM place_links WHERE place_id = $Into AND scheme = 'pleiades'"
        .query[String]
        .run()
        .toSet
    )
    assert(links.contains("999100007"), links.toString)
    // a site keeps its tolerance: Athens' finding is 4 km from Peiraieus, now a site
    assertEquals(at(2), Set(Athens, Peir))
    assertEquals(
      at(3),
      Set(Aiantis),
      "a people takes the finding on its point; the point is a placeholder, so no site does"
    )
    assertEquals(mine(PlaceFixtures.proposals(ds)), Nil, "a second run has nothing to do")
    // the fix re-matched only the changed places; a whole rebuild agrees with it
    val fixed = (0 to 5).map(at).toList
    val _ = PlaceFixtures.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)
    assertEquals((0 to 5).map(at).toList, fixed)

  test("place-grid-point: a linked record's real point is offered, and --fix moves the place there"):
    seed()
    connect(ds):
      val _ = sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, item_class, item_geom)
                    VALUES ($Thess, 'wikidata', 'Q999100004', 'close_match', 'Q1620908',
                            ST_SetSRID(ST_MakePoint(141.71, -47.62), 4326)),
                           ($Achaia, 'wikidata', 'Q999100005', 'close_match', 'Q1620908',
                            ST_SetSRID(ST_MakePoint(141.75, -47.25), 4326))""".update.run()
    val rs = PlaceFixtures.relocations(ds).filter(_.id.value.startsWith("pleiades_99910"))
    assertEquals(rs.map(_.id), List(Thess), "a linked point on a grid centre itself is no better")
    assert(
      rs.head.describe.value.contains("wikidata:Q999100004's point 47.6200 S") || rs.head.describe.value
        .contains("-47.6200"),
      rs.head.describe
    )
    // a finding ON the linked record's point: the moved place takes it by its new point
    SourceFixtures.upsert(
      ds,
      Source(
        citekey = Citekey("zzrc_src2"),
        title = com.histmersion.api.library.Title("Region cleanup fixture 2"),
        authors = (List("T")).map(com.histmersion.api.library.AuthorName.apply),
        doi = None,
        year = (Some(2026)).map(Year.apply),
        venue = None,
        status = Source.Status.Current,
        topics = Nil,
        regions = Nil,
        tags = Nil,
        period = None,
        summary = None,
        citedByCount = None,
        influentialCitations = None,
        provenance = None,
        calibreId = None,
        findings = List(
          Finding(
            Finding.Id("zzrc_src2#0"),
            Citekey("zzrc_src2"),
            Finding.Claim("on the real point"),
            None,
            Some(GeoPoint(Longitude(141.71), Latitude(-47.62))),
            None,
            None,
            Nil,
            None
          )
        )
      ),
      cfg.limits.chunkChars,
      cfg.places.pointToleranceKm
    )
    assertEquals(
      PlaceFixtures
        .placesOf(ds, Finding.Id("zzrc_src2#0"))
        .map(_.id)
        .filter(_.value.startsWith("pleiades_99910"))
        .toList,
      Nil
    )
    assertEquals(PlaceFixtures.relocate(ds, rs, cfg.places.pointToleranceKm), 1)
    assertEquals(
      PlaceFixtures
        .placesOf(ds, Finding.Id("zzrc_src2#0"))
        .map(_.id)
        .filter(_.value.startsWith("pleiades_99910"))
        .toList,
      List(Thess),
      "the moved place's findings are matched again at once"
    )
    assertEquals(
      PlaceFixtures.get(ds, ThessId).map(_.locationPrecision),
      Some(com.histmersion.api.catalogue.Place.Precision.Stated)
    )
    assertEquals(PlaceFixtures.relocations(ds).filter(_.id.value.startsWith("pleiades_99910")), Nil)
    assertEquals(
      PlaceFixtures.relocate(ds, rs, cfg.places.pointToleranceKm),
      0,
      "compare-and-set: moved once"
    )
