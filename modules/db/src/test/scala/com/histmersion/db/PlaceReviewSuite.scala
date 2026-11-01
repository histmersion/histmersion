package com.histmersion.db

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.places.PlaceReviewRepo

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.catalogue.places.PlaceReview.Proposal

import com.augustnagro.magnum.*
import com.histmersion.values.{LocationPrecision, PlaceKind, SourceStatus}

/** `lint --rule place-kind` and `--rule place-grid-point` (2026-10-09), against a real PostGIS
  * (HISTMERSION_TEST_DB_URL; skipped otherwise): the dry run lists people, polities, a settlement and a merge and
  * writes nothing; `--fix` changes the kinds, merges the duplicate (links, alias) and matches findings again; a
  * grid-square place moves to its linked record's real point, once. Fixtures: [[RegionCleanupFixtures]].
  */
class PlaceReviewSuite extends RegionCleanupFixtures:

  private def mine(ps: List[Proposal]) = ps.filter(p => p.id.startsWith("pleiades_99910") || p.id.startsWith("zzrc"))

  test("place-kind: a dry run lists people, polities, a settlement and a merge, and changes nothing"):
    seed()
    val ps = mine(connect(ds)(PlaceReviewRepo.proposals()))
    val byId = ps.map(p => p.id -> p).toMap
    assertEquals(byId.keySet, Set(Aiantis, Thess, Dup, Into, Peir))
    assertEquals(byId(Aiantis).asInstanceOf[Proposal.Reclassify].to, PlaceKind.People)
    assertEquals(byId(Thess).asInstanceOf[Proposal.Reclassify].to, PlaceKind.Polity)
    assertEquals(byId(Into).asInstanceOf[Proposal.Reclassify].to, PlaceKind.Polity, "province-2 filed as a site")
    assertEquals(byId(Peir).asInstanceOf[Proposal.Reclassify].to, PlaceKind.Site)
    assertEquals(byId(Dup).asInstanceOf[Proposal.Merge].into, Into)
    assert(byId(Aiantis).describe.startsWith(s"$Aiantis Zzrc Aiantis: region → people"), byId(Aiantis).describe)
    assertEquals(PlaceFixtures.get(ds, Aiantis).map(_.place.kind), Some(PlaceKind.Region), "a dry run writes nothing")

  test("place-kind --fix: kinds change, the duplicate merges (links, alias), findings are matched again"):
    seed()
    connect(ds):
      val _ = sql"""INSERT INTO place_links (place_id, scheme, external_id, relation)
                    VALUES ($Dup, 'pleiades', '999100007', 'exact_match') ON CONFLICT DO NOTHING""".update.run()
    val (re, mg) =
      transact(ds)(PlaceReviewRepo.apply(mine(connect(ds)(PlaceReviewRepo.proposals())), cfg.places.pointToleranceKm))
    assertEquals((re, mg), (4, 1))
    assertEquals(PlaceFixtures.get(ds, Aiantis).map(_.place.kind), Some(PlaceKind.People))
    assertEquals(PlaceFixtures.get(ds, Peir).map(_.place.kind), Some(PlaceKind.Site))
    assertEquals(PlaceFixtures.get(ds, Dup), None)
    assertEquals(PlaceFixtures.renamedTo(ds, Dup), Some(Into), "the old id resolves to its replacement")
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
    assertEquals(mine(connect(ds)(PlaceReviewRepo.proposals())), Nil, "a second run has nothing to do")
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
    val rs = connect(ds)(PlaceReviewRepo.relocations()).filter(_.id.startsWith("pleiades_99910"))
    assertEquals(rs.map(_.id), List(Thess), "a linked point on a grid centre itself is no better")
    assert(
      rs.head.describe.contains("wikidata:Q999100004's point 47.6200 S") || rs.head.describe.contains("-47.6200"),
      rs.head.describe
    )
    // a finding ON the linked record's point: the moved place takes it by its new point
    SourceRepo.upsert(
      ds,
      Source(
        citekey = "zzrc_src2",
        title = "Region cleanup fixture 2",
        authors = List("T"),
        doi = None,
        year = Some(2026),
        venue = None,
        status = SourceStatus.Current,
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
            "zzrc_src2#0",
            "zzrc_src2",
            "on the real point",
            None,
            Some(GeoPoint(141.71, -47.62)),
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
    assertEquals(PlaceFixtures.placesOf(ds, "zzrc_src2#0").map(_.id).filter(_.startsWith("pleiades_99910")).toList, Nil)
    assertEquals(transact(ds)(PlaceReviewRepo.relocate(rs, cfg.places.pointToleranceKm)), 1)
    assertEquals(
      PlaceFixtures.placesOf(ds, "zzrc_src2#0").map(_.id).filter(_.startsWith("pleiades_99910")).toList,
      List(Thess),
      "the moved place's findings are matched again at once"
    )
    assertEquals(PlaceFixtures.get(ds, Thess).map(_.locationPrecision), Some(LocationPrecision.Stated))
    assertEquals(connect(ds)(PlaceReviewRepo.relocations()).filter(_.id.startsWith("pleiades_99910")), Nil)
    assertEquals(
      transact(ds)(PlaceReviewRepo.relocate(rs, cfg.places.pointToleranceKm)),
      0,
      "compare-and-set: moved once"
    )
