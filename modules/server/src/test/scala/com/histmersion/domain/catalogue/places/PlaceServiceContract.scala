package com.histmersion
package domain.catalogue.places
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.{Dataset, Write}
import com.histmersion.domain.catalogue.CatalogueStoreSuite
import com.histmersion.api.catalogue.{Period, Place}
import com.histmersion.api.catalogue.Year
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{LintRule, tolerated}

/** The contract the gazetteer's records keep, alike in memory and in the database: an import's places, names and edges
  * read back (a dangling parent reported, never fatal); the search's order; a person's names (an imported one is kept);
  * a rename, an old id resolving; a person's own place; a kind and period set over the source's and reset; a merge and
  * a removal, neither undone by the next import. What only a database answers (a shape's GeoJSON, the findings matched
  * to a place, a point override) is the database suites'.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
abstract class PlaceServiceContract extends CatalogueStoreSuite:

  private val Tolerance = GeocodeSettings.ToleranceKm(10.0)
  private val RegionKind = Place.KindValue("region")
  private val SiteKind = Place.KindValue("site")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def square(x0: Double, y0: Double, x1: Double, y1: Double): Option[Place.Geometry] =
    Some(Place.Geometry(s"""{"type":"Polygon","coordinates":[[[$x0,$y0],[$x1,$y0],[$x1,$y1],[$x0,$y1],[$x0,$y0]]]}"""))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def point(x: Double, y: Double): Option[Place.Geometry] = Some(
    Place.Geometry(s"""{"type":"Point","coordinates":[$x,$y]}""")
  )

  private def place(
      id: Place.Id,
      label: Place.Label,
      kind: Place.KindValue,
      geometry: Option[Place.Geometry],
      period: Option[Period] = None
  ) =
    Place(id, label, kind, geometry, period, Some(Place.Source("zz test")))

  /** A region, a region inside it, two sites in that, one named twice; a site whose parent is nowhere. */
  private val batch = List(
    Place.Input(
      place(Place.Id("zzp_land"), Place.Label("Zzpland"), RegionKind, square(30, 36, 40, 42)),
      List(Place.Name(Place.Name.Value("Zzpland"), Place.Name.Lang("en")))
    ),
    Place.Input(
      place(
        Place.Id("zzp_march"),
        Place.Label("Zzpmarch"),
        RegionKind,
        square(33, 38, 36, 41),
        Some(Period(Year(-1600), Year(-1200)))
      ),
      Nil,
      List(Place.Relation(Place.Relation.ChildId("zzp_march"), Place.Relation.ParentId("zzp_land")))
    ),
    Place.Input(
      place(
        Place.Id("zzp_hill"),
        Place.Label("Zzphill"),
        SiteKind,
        point(34.6, 40.0),
        Some(Period(Year(-1650), Year(-1180)))
      ),
      List(
        Place.Name(Place.Name.Value("Zzphill"), Place.Name.Lang("en")),
        Place.Name(Place.Name.Value("Zzphügel"), Place.Name.Lang("de"))
      ),
      List(Place.Relation(Place.Relation.ChildId("zzp_hill"), Place.Relation.ParentId("zzp_march")))
    ),
    Place.Input(
      place(Place.Id("zzp_well"), Place.Label("Zzpwell"), SiteKind, point(34.9, 39.7)),
      Nil,
      List(Place.Relation(Place.Relation.ChildId("zzp_well"), Place.Relation.ParentId("zzp_march")))
    ),
    Place.Input(
      place(Place.Id("zzp_lost"), Place.Label("Zzplost"), SiteKind, point(25, 32)),
      Nil,
      List(Place.Relation(Place.Relation.ChildId("zzp_lost"), Place.Relation.ParentId("zzp_nowhere")))
    )
  )

  stest("an import's places, names and edges, read back; a dangling parent reported, not fatal") { s =>
    val p = s.places
    val r = p.importPlaces(batch, Dataset.Seed)
    assertEquals((r.places, r.names, r.relations), (5, 3, 3))
    assertEquals(r.failed, List("zzp_lost: no such parent 'zzp_nowhere'"))
    assertEquals(p.count(), 5)
    val hill = p.get(domain.catalogue.Place.Id("zzp_hill")).getOrElse(fail("imported"))
    assertEquals(
      (
        hill.place.id,
        hill.place.label,
        hill.place.kind,
        hill.place.periodStart,
        hill.place.periodEnd,
        hill.place.names
      ),
      ("zzp_hill", "Zzphill", "site", Some(-1650L), Some(-1180L), "Zzphill, Zzphügel")
    )
    assertEquals(hill.parents.map(_.id), List("zzp_march"))
    assertEquals(
      p.get(domain.catalogue.Place.Id("zzp_march")).map(_.children.map(_.id)),
      Some(List("zzp_hill", "zzp_well"))
    )
    assertEquals(
      p.children(domain.catalogue.Place.Id("zzp_march"), None, PageSize(1)).map(_.id),
      List("zzp_hill"),
      "the limit"
    )
    assertEquals(
      p.hierarchy(
        None,
        Some(domain.catalogue.Place.Id("zzp_land")),
        PlaceService.DepthLimit(5),
        PageSize(100)
      ).map(t => t.id -> t.depth),
      List("zzp_land" -> 0, "zzp_march" -> 1, "zzp_hill" -> 2, "zzp_well" -> 2)
    )
    assertEquals(
      p.hierarchy(
        None,
        Some(domain.catalogue.Place.Id("zzp_land")),
        PlaceService.DepthLimit(1),
        PageSize(100)
      ).map(_.id),
      List("zzp_land", "zzp_march"),
      "the depth"
    )
    assertEquals(p.get(domain.catalogue.Place.Id("zzp_none")), None)
    val scope =
      p.scopeFor(List(PlaceService.ScopeForValues("zzp_march"), PlaceService.ScopeForValues("Zzpnothing")), None)
    assertEquals(
      scope.idsFor(PlaceScope.Value("zzp_march")).map(_.toSet),
      Some(Set("zzp_march", "zzp_hill", "zzp_well"))
    )
    assertEquals(scope.idsFor(PlaceScope.Value("Zzpnothing")), None)
    assertEquals(scope.labelFor(PlaceScope.Value("zzp_march")), "Zzpmarch")
    val (nodes, edges) = p.gazetteer(List(domain.catalogue.Place.Id("zzp_hill")))
    assertEquals(nodes.map(_.id).toSet, Set("zzp_hill", "zzp_march", "zzp_land"))
    assertEquals(
      edges.map(e => e.childId -> e.parentId).toSet,
      Set("zzp_hill" -> "zzp_march", "zzp_march" -> "zzp_land")
    )
    // the same batch again: the same rows, nothing doubled
    val again = p.importPlaces(batch, Dataset.Seed)
    assertEquals((again.places, again.relations), (5, 3))
    assertEquals(p.count(), 5)
    assertEquals(
      p.get(domain.catalogue.Place.Id("zzp_hill")).map(_.place.names),
      Some("Zzphill, Zzphügel")
    )
  }

  stest("the search: exact label first, then prefixes, then any label or name containing it; a short query by key") {
    s =>
      val p = s.places
      val _ = p.importPlaces(
        List(
          Place.Input(place(Place.Id("zzq_c"), Place.Label("Bzzqa town"), SiteKind, point(1, 1))),
          Place.Input(place(Place.Id("zzq_b"), Place.Label("Zzqa harbour"), SiteKind, point(1, 2))),
          Place.Input(place(Place.Id("zzq_a"), Place.Label("Zzqa"), SiteKind, point(1, 3))),
          Place.Input(
            place(Place.Id("zzq_d"), Place.Label("Elsewhere"), SiteKind, point(1, 4)),
            List(Place.Name(Place.Name.Value("Old Zzqa"), Place.Name.Lang("en")))
          )
        ),
        Dataset.Seed
      )
      val hits = p.search(Some(PlaceService.SearchQ("zzqa")), None, PageSize(10))
      assertEquals(hits.map(_.id).take(2), List("zzq_a", "zzq_b"))
      assertEquals(hits.map(_.id).toSet, Set("zzq_a", "zzq_b", "zzq_c", "zzq_d"))
      assertEquals(hits.find(_.id == Place.Id("zzq_d")).flatMap(_.matchedName), Some("Old Zzqa"))
      assertEquals(
        p.search(Some(PlaceService.SearchQ("zzqa")), None, PageSize(1)).map(_.id),
        List("zzq_a"),
        "the limit"
      )
      assertEquals(p.search(Some(PlaceService.SearchQ("nothing like it")), None, PageSize(10)), Nil)
  }

  stest("a person's names: added, kept by the next import, an imported one not removable") { s =>
    val p = s.places
    val _ = p.importPlaces(batch, Dataset.Seed)
    val added = p
      .addName(
        domain.catalogue.Place.Id("zzp_hill"),
        Place.Name.Value("Zzphill on the river"),
        Place.Name.Lang("en")
      )
      .getOrElse(fail("added"))
    assertEquals((added.label, added.added, added.changed, added.wasImported), ("Zzphill", true, true, false))
    assertEquals(
      p.addName(
        domain.catalogue.Place.Id("zzp_hill"),
        Place.Name.Value("Zzphill on the river"),
        Place.Name.Lang("en")
      ).map(_.changed),
      Right(false),
      "already there"
    )
    assertEquals(
      p.addName(
        domain.catalogue.Place.Id("zzp_hill"),
        Place.Name.Value("Zzphügel"),
        Place.Name.Lang("de")
      ).map(c => (c.changed, c.wasImported)),
      Right((true, true))
    )
    assertEquals(
      p.manualNames(Some(domain.catalogue.Place.Id("zzp_hill"))).map(_.name),
      List("Zzphill on the river", "Zzphügel")
    )
    val _ = p.importPlaces(batch, Dataset.Seed)
    assertEquals(p.manualNames(None).map(n => n.placeId -> n.name).size, 2, "the next import keeps them")
    assertEquals(
      p.removeName(
        domain.catalogue.Place.Id("zzp_hill"),
        Place.Name.Value("Zzphill"),
        Place.Name.Lang("en")
      ).left
        .map(_.code),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      p.removeName(
        domain.catalogue.Place.Id("zzp_hill"),
        Place.Name.Value("Zzpnever"),
        Place.Name.Lang("")
      ).left
        .map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(
      p.removeName(domain.catalogue.Place.Id("zzp_none"), Place.Name.Value("x"), Place.Name.Lang(""))
        .left
        .map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(
      p.removeName(
        domain.catalogue.Place.Id("zzp_hill"),
        Place.Name.Value("Zzphill on the river"),
        Place.Name.Lang("en")
      ).map(c => (c.added, c.changed)),
      Right((false, true))
    )
    assertEquals(
      p.manualNames(Some(domain.catalogue.Place.Id("zzp_hill"))).map(_.name),
      List("Zzphügel")
    )
    assertEquals(
      p.addName(domain.catalogue.Place.Id("zzp_none"), Place.Name.Value("x"), Place.Name.Lang(""))
        .left
        .map(_.code),
      Left(Some(Write.CodeNotFound))
    )
  }

  stest("a rename: the edges follow, the old id resolves in a filter, a taken id or an unknown one refused") { s =>
    val p = s.places
    val _ = p.importPlaces(batch, Dataset.Seed)
    assertEquals(
      p.rename(
        domain.catalogue.Place.Id("zzp_march"),
        domain.catalogue.Place.Id("zzp_marches")
      ),
      Right(())
    )
    assertEquals(p.get(domain.catalogue.Place.Id("zzp_march")), None)
    assertEquals(
      p.get(domain.catalogue.Place.Id("zzp_hill")).map(_.parents.map(_.id)),
      Some(List("zzp_marches"))
    )
    assertEquals(
      p.get(domain.catalogue.Place.Id("zzp_marches")).map(_.children.map(_.id)),
      Some(List("zzp_hill", "zzp_well"))
    )
    assertEquals(
      p.scopeFor(List(PlaceService.ScopeForValues("zzp_march")), None)
        .idsFor(PlaceScope.Value("zzp_march"))
        .map(_.toSet)
        .map(_(domain.catalogue.Place.Id("zzp_hill"))),
      Some(true)
    )
    assertEquals(
      p.rename(
        domain.catalogue.Place.Id("zzp_hill"),
        domain.catalogue.Place.Id("zzp_well")
      ).left
        .map(_.code),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      p.rename(
        domain.catalogue.Place.Id("zzp_gone"),
        domain.catalogue.Place.Id("zzp_new")
      ).left
        .map(_.code),
      Left(Some(Write.CodeNotFound))
    )
  }

  stest("a person's own place: added once, refused when its id is taken or an old id") { s =>
    val p = s.places
    val _ = p.importPlaces(batch, Dataset.Seed)
    val own = Place.Input(
      place(Place.Id("local_zzp_cairn"), Place.Label("Zzpcairn"), SiteKind, point(34.7, 40.1)),
      List(Place.Name(Place.Name.Value("Zzpcairn")))
    )
    assertEquals(p.add(own).map(x => (x.id, x.label, x.kind)), Right(("local_zzp_cairn", "Zzpcairn", "site")))
    assertEquals(p.add(own).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(
      p.rename(
        domain.catalogue.Place.Id("zzp_well"),
        domain.catalogue.Place.Id("zzp_spring")
      ),
      Right(())
    )
    val again = Place.Input(place(Place.Id("zzp_well"), Place.Label("Zzpwell"), SiteKind, point(1, 1)))
    assertEquals(p.add(again).left.map(_.code), Left(Some(Write.CodeInvalid)), "an old id")
    assertEquals(p.count(), 6)
  }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def changed(c: Place.OverrideChanged) =
    (c.placeId, c.label, c.reset, c.kindBefore, c.kindAfter, c.periodBefore.value: String, c.periodAfter.value: String)

  stest("a kind and period set over the source's, listed, kept by the next import, and reset") { s =>
    val p = s.places
    val _ = p.importPlaces(batch, Dataset.Seed)
    val set = p.setOverride(
      domain.catalogue.Place.Id("zzp_hill"),
      Place.OverrideEdit.Put(
        Some(Place.OverrideEdit.Put.Kind("polity")),
        Some((Some(Year(-1500L)), Some(Year(-1100L)))),
        Some(Place.Rationale("the survey"))
      ),
      Tolerance
    )
    assertEquals(
      set.map(changed),
      Right(
        (
          "zzp_hill",
          "Zzphill",
          false,
          "site",
          "polity",
          Place.Overrides.periodText(Some(Year(-1650)), Some(Year(-1180))),
          Place.Overrides.periodText(Some(Year(-1500)), Some(Year(-1100)))
        )
      )
    )
    assertEquals(
      p.get(domain.catalogue.Place.Id("zzp_hill"))
        .map(d => (d.place.kind, d.place.periodStart, d.place.periodEnd)),
      Some(("polity", Some(-1500L), Some(-1100L)))
    )
    val listed = p.overrides(Some(domain.catalogue.Place.Id("zzp_hill")))
    assertEquals(
      listed.map(o => (o.placeId, o.kind, o.periodStart, o.periodEnd, o.periodSet, o.reason, o.sourceKind)),
      List(("zzp_hill", Some("polity"), Some(-1500L), Some(-1100L), true, Some(Place.Rationale("the survey")), "site"))
    )
    assertEquals(listed.map(o => (o.sourcePeriodStart, o.sourcePeriodEnd)), List((Some(-1650L), Some(-1180L))))
    assertEquals(p.overrides(None).map(_.placeId), List("zzp_hill"))
    val _ = p.importPlaces(batch, Dataset.Seed)
    assertEquals(
      p.get(domain.catalogue.Place.Id("zzp_hill")).map(_.place.kind),
      Some("polity"),
      "the next import keeps it"
    )
    val reset =
      p.setOverride(domain.catalogue.Place.Id("zzp_hill"), Place.OverrideEdit.Reset, Tolerance)
    assertEquals(reset.map(c => (c.reset, c.kindAfter)), Right((true, "site")))
    assertEquals(
      p.get(domain.catalogue.Place.Id("zzp_hill")).map(d => (d.place.kind, d.place.periodStart)),
      Some(("site", Some(-1650L)))
    )
    assertEquals(
      p.setOverride(domain.catalogue.Place.Id("zzp_hill"), Place.OverrideEdit.Reset, Tolerance)
        .left
        .map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(
      p.setOverride(domain.catalogue.Place.Id("zzp_none"), Place.OverrideEdit.Reset, Tolerance)
        .left
        .map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(p.overrides(None), Nil)
  }

  stest("a merge and a removal: what referred to the place moves or refuses, and no import brings it back") { s =>
    val p = s.places
    val _ = p.importPlaces(batch, Dataset.Seed)
    val gone = p
      .merge(
        domain.catalogue.Place.Id("zzp_well"),
        domain.catalogue.Place.Id("zzp_hill"),
        Tolerance
      )
      .getOrElse(fail("merged"))
    assertEquals(
      (gone.placeId, gone.label, gone.into, gone.intoLabel),
      ("zzp_well", "Zzpwell", Some("zzp_hill"), Some("Zzphill"))
    )
    assertEquals(p.get(domain.catalogue.Place.Id("zzp_well")), None)
    assertEquals(
      p.scopeFor(List(PlaceService.ScopeForValues("zzp_well")), None)
        .idsFor(PlaceScope.Value("zzp_well"))
        .map(_.contains("zzp_hill")),
      Some(true)
    )
    assertEquals(
      p.merge(
        domain.catalogue.Place.Id("zzp_hill"),
        domain.catalogue.Place.Id("zzp_hill"),
        Tolerance
      ).left
        .map(_.code),
      Left(Some(Write.CodeInvalid))
    )
    assertEquals(
      p.merge(
        domain.catalogue.Place.Id("zzp_none"),
        domain.catalogue.Place.Id("zzp_hill"),
        Tolerance
      ).left
        .map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(
      p.remove(
        domain.catalogue.Place.Id("zzp_march"),
        Some(Place.Rationale("a child"))
      ).left
        .map(_.code),
      Left(Some(Write.CodeInvalid)),
      "it has a child"
    )
    assertEquals(
      p.remove(
        domain.catalogue.Place.Id("zzp_lost"),
        Some(Place.Rationale("a phantom"))
      ).map(g => (g.placeId, g.into)),
      Right(("zzp_lost", None))
    )
    assertEquals(
      p.remove(domain.catalogue.Place.Id("zzp_lost"), None).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    val _ = p.importPlaces(batch, Dataset.Seed)
    assertEquals(p.get(domain.catalogue.Place.Id("zzp_lost")), None, "removed for good")
    assertEquals(p.get(domain.catalogue.Place.Id("zzp_well")), None, "merged for good")
    assertEquals(p.count(), 3)
  }
