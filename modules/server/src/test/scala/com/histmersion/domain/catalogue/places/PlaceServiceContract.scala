package com.histmersion.domain.catalogue.places

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.{Dataset, Write}
import com.histmersion.domain.catalogue.CatalogueStoreSuite
import com.histmersion.api.catalogue.{Period, Place}

/** The contract the gazetteer's records keep, alike in memory and in the database: an import's places, names and edges
  * read back (a dangling parent reported, never fatal); the search's order; a person's names (an imported one is kept);
  * a rename, an old id resolving; a person's own place; a kind and period set over the source's and reset; a merge and
  * a removal, neither undone by the next import. What only a database answers (a shape's GeoJSON, the findings matched
  * to a place, a point override) is the database suites'.
  */
abstract class PlaceServiceContract extends CatalogueStoreSuite:

  private def square(x0: Double, y0: Double, x1: Double, y1: Double): Option[String] =
    Some(s"""{"type":"Polygon","coordinates":[[[$x0,$y0],[$x1,$y0],[$x1,$y1],[$x0,$y1],[$x0,$y0]]]}""")
  private def point(x: Double, y: Double): Option[String] = Some(s"""{"type":"Point","coordinates":[$x,$y]}""")

  private def place(id: String, label: String, kind: String, geometry: Option[String], period: Option[Period] = None) =
    Place(com.histmersion.api.catalogue.Place.Id(id), label, kind, geometry, period, Some(com.histmersion.api.catalogue.Place.Source("zz test")))

  /** A region, a region inside it, two sites in that, one named twice; a site whose parent is nowhere. */
  private val batch = List(
    Place.Input(place("zzp_land", "Zzpland", "region", square(30, 36, 40, 42)), List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzpland"), com.histmersion.api.catalogue.Place.Name.Lang("en")))),
    Place.Input(
      place("zzp_march", "Zzpmarch", "region", square(33, 38, 36, 41), Some(Period(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200)))),
      Nil,
      List(Place.Relation(com.histmersion.api.catalogue.Place.Relation.ChildId("zzp_march"), com.histmersion.api.catalogue.Place.Relation.ParentId("zzp_land")))
    ),
    Place.Input(
      place("zzp_hill", "Zzphill", "site", point(34.6, 40.0), Some(Period(com.histmersion.api.catalogue.Year(-1650), com.histmersion.api.catalogue.Year(-1180)))),
      List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzphill"), com.histmersion.api.catalogue.Place.Name.Lang("en")), Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzphügel"), com.histmersion.api.catalogue.Place.Name.Lang("de"))),
      List(Place.Relation(com.histmersion.api.catalogue.Place.Relation.ChildId("zzp_hill"), com.histmersion.api.catalogue.Place.Relation.ParentId("zzp_march")))
    ),
    Place.Input(
      place("zzp_well", "Zzpwell", "site", point(34.9, 39.7)),
      Nil,
      List(Place.Relation(com.histmersion.api.catalogue.Place.Relation.ChildId("zzp_well"), com.histmersion.api.catalogue.Place.Relation.ParentId("zzp_march")))
    ),
    Place.Input(
      place("zzp_lost", "Zzplost", "site", point(25, 32)),
      Nil,
      List(Place.Relation(com.histmersion.api.catalogue.Place.Relation.ChildId("zzp_lost"), com.histmersion.api.catalogue.Place.Relation.ParentId("zzp_nowhere")))
    )
  )

  stest("an import's places, names and edges, read back; a dangling parent reported, not fatal") { s =>
    val p = s.places
    val r = p.importPlaces(batch, Dataset.Seed)
    assertEquals((r.places, r.names, r.relations), (5, 3, 3))
    assertEquals(r.failed, List("zzp_lost: no such parent 'zzp_nowhere'"))
    assertEquals(p.count(), 5)
    val hill = p.get(com.histmersion.domain.catalogue.Place.Id("zzp_hill")).getOrElse(fail("imported"))
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
    assertEquals(p.get(com.histmersion.domain.catalogue.Place.Id("zzp_march")).map(_.children.map(_.id)), Some(List("zzp_hill", "zzp_well")))
    assertEquals(p.children(com.histmersion.domain.catalogue.Place.Id("zzp_march"), None, com.histmersion.PageSize(1)).map(_.id), List("zzp_hill"), "the limit")
    assertEquals(
      p.hierarchy(None, Some(com.histmersion.domain.catalogue.Place.Id("zzp_land")), com.histmersion.domain.catalogue.places.PlaceService.DepthLimit(5), com.histmersion.PageSize(100)).map(t => t.id -> t.depth),
      List("zzp_land" -> 0, "zzp_march" -> 1, "zzp_hill" -> 2, "zzp_well" -> 2)
    )
    assertEquals(p.hierarchy(None, Some(com.histmersion.domain.catalogue.Place.Id("zzp_land")), com.histmersion.domain.catalogue.places.PlaceService.DepthLimit(1), com.histmersion.PageSize(100)).map(_.id), List("zzp_land", "zzp_march"), "the depth")
    assertEquals(p.get(com.histmersion.domain.catalogue.Place.Id("zzp_none")), None)
    val scope = p.scopeFor(List(com.histmersion.domain.catalogue.places.PlaceService.ScopeForValues("zzp_march"), com.histmersion.domain.catalogue.places.PlaceService.ScopeForValues("Zzpnothing")), None)
    assertEquals(scope.idsFor(com.histmersion.domain.catalogue.places.PlaceScope.Value("zzp_march")).map(_.toSet), Some(Set("zzp_march", "zzp_hill", "zzp_well")))
    assertEquals(scope.idsFor(com.histmersion.domain.catalogue.places.PlaceScope.Value("Zzpnothing")), None)
    assertEquals(scope.labelFor(com.histmersion.domain.catalogue.places.PlaceScope.Value("zzp_march")), "Zzpmarch")
    val (nodes, edges) = p.gazetteer(List(com.histmersion.domain.catalogue.Place.Id("zzp_hill")))
    assertEquals(nodes.map(_.id).toSet, Set("zzp_hill", "zzp_march", "zzp_land"))
    assertEquals(
      edges.map(e => e.childId -> e.parentId).toSet,
      Set("zzp_hill" -> "zzp_march", "zzp_march" -> "zzp_land")
    )
    // the same batch again: the same rows, nothing doubled
    val again = p.importPlaces(batch, Dataset.Seed)
    assertEquals((again.places, again.relations), (5, 3))
    assertEquals(p.count(), 5)
    assertEquals(p.get(com.histmersion.domain.catalogue.Place.Id("zzp_hill")).map(_.place.names), Some("Zzphill, Zzphügel"))
  }

  stest("the search: exact label first, then prefixes, then any label or name containing it; a short query by key") {
    s =>
      val p = s.places
      val _ = p.importPlaces(
        List(
          Place.Input(place("zzq_c", "Bzzqa town", "site", point(1, 1))),
          Place.Input(place("zzq_b", "Zzqa harbour", "site", point(1, 2))),
          Place.Input(place("zzq_a", "Zzqa", "site", point(1, 3))),
          Place.Input(place("zzq_d", "Elsewhere", "site", point(1, 4)), List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Old Zzqa"), com.histmersion.api.catalogue.Place.Name.Lang("en"))))
        ),
        Dataset.Seed
      )
      val hits = p.search(Some(com.histmersion.domain.catalogue.places.PlaceService.SearchQ("zzqa")), None, com.histmersion.PageSize(10))
      assertEquals(hits.map(_.id).take(2), List("zzq_a", "zzq_b"))
      assertEquals(hits.map(_.id).toSet, Set("zzq_a", "zzq_b", "zzq_c", "zzq_d"))
      assertEquals(hits.find(_.id == "zzq_d").flatMap(_.matchedName), Some("Old Zzqa"))
      assertEquals(p.search(Some(com.histmersion.domain.catalogue.places.PlaceService.SearchQ("zzqa")), None, com.histmersion.PageSize(1)).map(_.id), List("zzq_a"), "the limit")
      assertEquals(p.search(Some(com.histmersion.domain.catalogue.places.PlaceService.SearchQ("nothing like it")), None, com.histmersion.PageSize(10)), Nil)
  }

  stest("a person's names: added, kept by the next import, an imported one not removable") { s =>
    val p = s.places
    val _ = p.importPlaces(batch, Dataset.Seed)
    val added = p.addName(com.histmersion.domain.catalogue.Place.Id("zzp_hill"), com.histmersion.api.catalogue.Place.Name.Value("Zzphill on the river"), com.histmersion.api.catalogue.Place.Name.Lang("en")).getOrElse(fail("added"))
    assertEquals((added.label, added.added, added.changed, added.wasImported), ("Zzphill", true, true, false))
    assertEquals(p.addName(com.histmersion.domain.catalogue.Place.Id("zzp_hill"), com.histmersion.api.catalogue.Place.Name.Value("Zzphill on the river"), com.histmersion.api.catalogue.Place.Name.Lang("en")).map(_.changed), Right(false), "already there")
    assertEquals(p.addName(com.histmersion.domain.catalogue.Place.Id("zzp_hill"), com.histmersion.api.catalogue.Place.Name.Value("Zzphügel"), com.histmersion.api.catalogue.Place.Name.Lang("de")).map(c => (c.changed, c.wasImported)), Right((true, true)))
    assertEquals(
      p.manualNames(Some(com.histmersion.domain.catalogue.Place.Id("zzp_hill"))).map(_.name),
      List("Zzphill on the river", "Zzphügel")
    )
    val _ = p.importPlaces(batch, Dataset.Seed)
    assertEquals(p.manualNames(None).map(n => n.placeId -> n.name).size, 2, "the next import keeps them")
    assertEquals(p.removeName(com.histmersion.domain.catalogue.Place.Id("zzp_hill"), com.histmersion.api.catalogue.Place.Name.Value("Zzphill"), com.histmersion.api.catalogue.Place.Name.Lang("en")).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(p.removeName(com.histmersion.domain.catalogue.Place.Id("zzp_hill"), com.histmersion.api.catalogue.Place.Name.Value("Zzpnever"), com.histmersion.api.catalogue.Place.Name.Lang("")).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(p.removeName(com.histmersion.domain.catalogue.Place.Id("zzp_none"), com.histmersion.api.catalogue.Place.Name.Value("x"), com.histmersion.api.catalogue.Place.Name.Lang("")).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(
      p.removeName(com.histmersion.domain.catalogue.Place.Id("zzp_hill"), com.histmersion.api.catalogue.Place.Name.Value("Zzphill on the river"), com.histmersion.api.catalogue.Place.Name.Lang("en")).map(c => (c.added, c.changed)),
      Right((false, true))
    )
    assertEquals(p.manualNames(Some(com.histmersion.domain.catalogue.Place.Id("zzp_hill"))).map(_.name), List("Zzphügel"))
    assertEquals(p.addName(com.histmersion.domain.catalogue.Place.Id("zzp_none"), com.histmersion.api.catalogue.Place.Name.Value("x"), com.histmersion.api.catalogue.Place.Name.Lang("")).left.map(_.code), Left(Some(Write.CodeNotFound)))
  }

  stest("a rename: the edges follow, the old id resolves in a filter, a taken id or an unknown one refused") { s =>
    val p = s.places
    val _ = p.importPlaces(batch, Dataset.Seed)
    assertEquals(p.rename(com.histmersion.domain.catalogue.Place.Id("zzp_march"), com.histmersion.domain.catalogue.Place.Id("zzp_marches")), Right(()))
    assertEquals(p.get(com.histmersion.domain.catalogue.Place.Id("zzp_march")), None)
    assertEquals(p.get(com.histmersion.domain.catalogue.Place.Id("zzp_hill")).map(_.parents.map(_.id)), Some(List("zzp_marches")))
    assertEquals(p.get(com.histmersion.domain.catalogue.Place.Id("zzp_marches")).map(_.children.map(_.id)), Some(List("zzp_hill", "zzp_well")))
    assertEquals(p.scopeFor(List(com.histmersion.domain.catalogue.places.PlaceService.ScopeForValues("zzp_march")), None).idsFor(com.histmersion.domain.catalogue.places.PlaceScope.Value("zzp_march")).map(_.toSet).map(_(com.histmersion.domain.catalogue.Place.Id("zzp_hill"))), Some(true))
    assertEquals(p.rename(com.histmersion.domain.catalogue.Place.Id("zzp_hill"), com.histmersion.domain.catalogue.Place.Id("zzp_well")).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(p.rename(com.histmersion.domain.catalogue.Place.Id("zzp_gone"), com.histmersion.domain.catalogue.Place.Id("zzp_new")).left.map(_.code), Left(Some(Write.CodeNotFound)))
  }

  stest("a person's own place: added once, refused when its id is taken or an old id") { s =>
    val p = s.places
    val _ = p.importPlaces(batch, Dataset.Seed)
    val own = Place.Input(place("local_zzp_cairn", "Zzpcairn", "site", point(34.7, 40.1)), List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zzpcairn"))))
    assertEquals(p.add(own).map(x => (x.id, x.label, x.kind)), Right(("local_zzp_cairn", "Zzpcairn", "site")))
    assertEquals(p.add(own).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(p.rename(com.histmersion.domain.catalogue.Place.Id("zzp_well"), com.histmersion.domain.catalogue.Place.Id("zzp_spring")), Right(()))
    val again = Place.Input(place("zzp_well", "Zzpwell", "site", point(1, 1)))
    assertEquals(p.add(again).left.map(_.code), Left(Some(Write.CodeInvalid)), "an old id")
    assertEquals(p.count(), 6)
  }

  private def changed(c: Place.OverrideChanged) =
    (c.placeId, c.label, c.reset, c.kindBefore, c.kindAfter, c.periodBefore, c.periodAfter)

  stest("a kind and period set over the source's, listed, kept by the next import, and reset") { s =>
    val p = s.places
    val _ = p.importPlaces(batch, Dataset.Seed)
    val set = p.setOverride(
      com.histmersion.domain.catalogue.Place.Id("zzp_hill"),
      Place.OverrideEdit.Put(Some(com.histmersion.api.catalogue.Place.OverrideEdit.Put.Kind("polity")), Some((Some(com.histmersion.api.catalogue.Year(-1500L)), Some(com.histmersion.api.catalogue.Year(-1100L)))), Some(com.histmersion.api.catalogue.Place.OverrideEdit.Put.Reason("the survey"))),
      10.0
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
          Place.Overrides.periodText(Some(com.histmersion.api.catalogue.Year(-1650)), Some(com.histmersion.api.catalogue.Year(-1180))),
          Place.Overrides.periodText(Some(com.histmersion.api.catalogue.Year(-1500)), Some(com.histmersion.api.catalogue.Year(-1100)))
        )
      )
    )
    assertEquals(
      p.get(com.histmersion.domain.catalogue.Place.Id("zzp_hill")).map(d => (d.place.kind, d.place.periodStart, d.place.periodEnd)),
      Some(("polity", Some(-1500L), Some(-1100L)))
    )
    val listed = p.overrides(Some(com.histmersion.domain.catalogue.Place.Id("zzp_hill")))
    assertEquals(
      listed.map(o => (o.placeId, o.kind, o.periodStart, o.periodEnd, o.periodSet, o.reason, o.sourceKind)),
      List(("zzp_hill", Some("polity"), Some(-1500L), Some(-1100L), true, Some("the survey"), "site"))
    )
    assertEquals(listed.map(o => (o.sourcePeriodStart, o.sourcePeriodEnd)), List((Some(-1650L), Some(-1180L))))
    assertEquals(p.overrides(None).map(_.placeId), List("zzp_hill"))
    val _ = p.importPlaces(batch, Dataset.Seed)
    assertEquals(p.get(com.histmersion.domain.catalogue.Place.Id("zzp_hill")).map(_.place.kind), Some("polity"), "the next import keeps it")
    val reset = p.setOverride(com.histmersion.domain.catalogue.Place.Id("zzp_hill"), Place.OverrideEdit.Reset, 10.0)
    assertEquals(reset.map(c => (c.reset, c.kindAfter)), Right((true, "site")))
    assertEquals(p.get(com.histmersion.domain.catalogue.Place.Id("zzp_hill")).map(d => (d.place.kind, d.place.periodStart)), Some(("site", Some(-1650L))))
    assertEquals(
      p.setOverride(com.histmersion.domain.catalogue.Place.Id("zzp_hill"), Place.OverrideEdit.Reset, 10.0).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(
      p.setOverride(com.histmersion.domain.catalogue.Place.Id("zzp_none"), Place.OverrideEdit.Reset, 10.0).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(p.overrides(None), Nil)
  }

  stest("a merge and a removal: what referred to the place moves or refuses, and no import brings it back") { s =>
    val p = s.places
    val _ = p.importPlaces(batch, Dataset.Seed)
    val gone = p.merge(com.histmersion.domain.catalogue.Place.Id("zzp_well"), com.histmersion.domain.catalogue.Place.Id("zzp_hill"), 10.0).getOrElse(fail("merged"))
    assertEquals(
      (gone.placeId, gone.label, gone.into, gone.intoLabel),
      ("zzp_well", "Zzpwell", Some("zzp_hill"), Some("Zzphill"))
    )
    assertEquals(p.get(com.histmersion.domain.catalogue.Place.Id("zzp_well")), None)
    assertEquals(p.scopeFor(List(com.histmersion.domain.catalogue.places.PlaceService.ScopeForValues("zzp_well")), None).idsFor(com.histmersion.domain.catalogue.places.PlaceScope.Value("zzp_well")).map(_.contains("zzp_hill")), Some(true))
    assertEquals(p.merge(com.histmersion.domain.catalogue.Place.Id("zzp_hill"), com.histmersion.domain.catalogue.Place.Id("zzp_hill"), 10.0).left.map(_.code), Left(Some(Write.CodeInvalid)))
    assertEquals(p.merge(com.histmersion.domain.catalogue.Place.Id("zzp_none"), com.histmersion.domain.catalogue.Place.Id("zzp_hill"), 10.0).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals(
      p.remove(com.histmersion.domain.catalogue.Place.Id("zzp_march"), Some(com.histmersion.api.catalogue.Place.Override.Reason("a child"))).left.map(_.code),
      Left(Some(Write.CodeInvalid)),
      "it has a child"
    )
    assertEquals(p.remove(com.histmersion.domain.catalogue.Place.Id("zzp_lost"), Some(com.histmersion.api.catalogue.Place.Override.Reason("a phantom"))).map(g => (g.placeId, g.into)), Right(("zzp_lost", None)))
    assertEquals(p.remove(com.histmersion.domain.catalogue.Place.Id("zzp_lost"), None).left.map(_.code), Left(Some(Write.CodeNotFound)))
    val _ = p.importPlaces(batch, Dataset.Seed)
    assertEquals(p.get(com.histmersion.domain.catalogue.Place.Id("zzp_lost")), None, "removed for good")
    assertEquals(p.get(com.histmersion.domain.catalogue.Place.Id("zzp_well")), None, "merged for good")
    assertEquals(p.count(), 3)
  }
