package com.histmersion.domain.catalogue.places

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.{Api, SpatialHierarchyResult}
import com.histmersion.config.AppConfig
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.catalogue.Geocode
import com.histmersion.domain.catalogue.Place

/** The gazetteer's controllers over the in-memory records, no server and no database: an LPF document imported (a bad
  * kind or document refused whole), an old id answering as its place, the tree's configured depth, a person's names and
  * overrides parsed at the boundary, the map's pick checked, and every write refused while the maintenance lock is
  * held. The map's shapes, the geometry and the geocoder are mechanisms over PostGIS: here they answer what they were
  * asked.
  */
class PlacesControllersSuite extends munit.FunSuite:
  private val cfg = AppConfig.load()

  private val shapes: PlaceMap = (kind, time, limit, _) => PlaceDetailRow.Geometry(s"$kind $time $limit")
  private val geometry: PlaceGeometry = new PlaceGeometry:
    def rebuildFindingPlaces(toleranceKm: GeocodeSettings.ToleranceKm) = com.histmersion.api.catalogue.Place.RebuildResult(com.histmersion.api.catalogue.Place.RebuildResult.Links(0), com.histmersion.api.catalogue.Place.RebuildResult.ByGeometry(0), com.histmersion.api.catalogue.Place.RebuildResult.ByName(0), com.histmersion.api.catalogue.Place.RebuildResult.Located(0), com.histmersion.api.catalogue.Place.RebuildResult.Unlocated(0), com.histmersion.api.catalogue.Place.RebuildResult.Places(0))
    def buildHierarchy() = SpatialHierarchyResult(com.histmersion.api.SpatialHierarchyResult.EdgesCreated(0), com.histmersion.api.SpatialHierarchyResult.PolygonPlaces(0), com.histmersion.api.SpatialHierarchyResult.PointPlaces(0))
    def replaceLand(geometries: List[PlaceDetailRow.Geometry]) = com.histmersion.RowCount(geometries.size)
    def reclip() = com.histmersion.RowCount(0)
    def reclassify(toleranceKm: GeocodeSettings.ToleranceKm) = (com.histmersion.RowCount(0), com.histmersion.RowCount(0))
    def relocate(toleranceKm: GeocodeSettings.ToleranceKm) = com.histmersion.RowCount(0)
    def removePointAreas(settings: PointAreaRepo.Settings) = PointAreaRepo.Removal(com.histmersion.domain.catalogue.places.PointAreaRepo.Removal.Removed(0), com.histmersion.domain.catalogue.places.PointAreaRepo.Removal.Remaining(0), com.histmersion.domain.catalogue.places.PointAreaRepo.Removal.Findings(0), com.histmersion.domain.catalogue.places.PointAreaRepo.Removal.Sources(0))
  private val geocoder: Geocoding = (name, _, _, _) =>
    Option.when(name == Geocode.Attempt.Name("Zzcl"))(Geocode.Resolution(Geocode.Resolution.Asked(name.value), com.histmersion.api.catalogue.Place.Id("zzcl_hill"), com.histmersion.api.catalogue.Place.Label("Zzclhill"), com.histmersion.api.catalogue.Longitude(34.6), com.histmersion.api.catalogue.Latitude(40.0)))

  private def controllers(gate: WriteGate = WriteGate()): (PlacesControllers, PlaceService) =
    val state = AtomicReference(CatalogueState.empty)
    val places = PlaceService.InMemory(state, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    val current: (AliasKind, PlacesControllers.Alias) => Place.Id = (_, id) =>
      state.get.placeAliases.get(CatalogueState.PlaceAliasesKey(id.value)).fold(Place.Id.assume(id.value))(a => Place.Id.assume(a.value))
    (PlacesControllers(places, shapes, geometry, geocoder, current, () => cfg, (l, d) => l.getOrElse(d), gate), places)

  private val doc = PlacesControllers.DatasetBody(
    """{"type":"FeatureCollection","features":[
      |{"@id":"zzcl_land","type":"Feature","properties":{"title":"Zzclland"},
      | "geometry":{"type":"Polygon","coordinates":[[[30,36],[40,36],[40,42],[30,42],[30,36]]]}},
      |{"@id":"zzcl_hill","type":"Feature","properties":{"title":"Zzclhill"},
      | "names":[{"toponym":"Zzclhill","lang":"en"}],
      | "geometry":{"type":"Point","coordinates":[34.6,40.0]},
      | "relations":[{"relationType":"gvp:broaderPartitive","relationTo":"zzcl_land"}]}
      |]}""".stripMargin)

  test("an LPF document imported, its places read back, an old id answering as its place; a bad import refused whole"):
    val (c, _) = controllers()
    val r = c.importPlaces(Caller.Local, Some(com.histmersion.domain.catalogue.Place.Kind.Wire("site")), Some(com.histmersion.api.Dataset.Wire("seed")), doc).fold(e => fail(e.message.value), identity)
    assertEquals((r.places, r.relations), (2, 1))
    assertEquals(c.search(Some(com.histmersion.domain.catalogue.places.PlaceService.SearchQ("zzcl")), None, None).map(_.id).toSet, Set("zzcl_land", "zzcl_hill"))
    assertEquals(c.children(com.histmersion.domain.catalogue.Place.Id("zzcl_land"), None).map(_.map(_.id)), Right(List("zzcl_hill")))
    assertEquals(c.tree(com.histmersion.domain.catalogue.Place.Id("zzcl_land"), None, None).map(_.map(_.id)), Right(List("zzcl_land", "zzcl_hill")))
    assertEquals(c.rename(Caller.Local, com.histmersion.domain.catalogue.Place.Id("zzcl_hill"), _ => Right(com.histmersion.domain.catalogue.Place.Id("zzcl_mound"))), Right(("zzcl_hill", "zzcl_mound")))
    assertEquals(c.get(com.histmersion.domain.catalogue.Place.Id("zzcl_hill"), None).map(_.place.id), Right("zzcl_mound"), "the old id answers")
    assertEquals(c.links(com.histmersion.domain.catalogue.Place.Id("zzcl_none")).left.map(_.message), Left("No place 'zzcl_none'"))
    assert(c.importPlaces(Caller.Local, Some(com.histmersion.domain.catalogue.Place.Kind.Wire("nonsense")), None, doc).left.exists(_.message.value.contains("unknown kind")))
    assert(c.importPlaces(Caller.Local, None, Some(com.histmersion.api.Dataset.Wire("other")), doc).left.exists(_.message.value.contains("seed or local")))
    assert(c.importPlaces(Caller.Local, None, None, com.histmersion.domain.catalogue.places.PlacesControllers.DatasetBody("not json")).isLeft)

  test("names and overrides parsed at the boundary; the map's pick checked; the geocoder asked"):
    val (c, _) = controllers()
    val _ = c.importPlaces(Caller.Local, Some(com.histmersion.domain.catalogue.Place.Kind.Wire("site")), None, doc)
    assert(
      c.addName(Caller.Local, com.histmersion.domain.catalogue.Place.Id("zzcl_hill"), com.histmersion.api.catalogue.Place.NameAdd(com.histmersion.api.catalogue.Place.NameAdd.Name("  "))).isLeft,
      "a blank name"
    )
    assertEquals(
      c.addName(Caller.Local, com.histmersion.domain.catalogue.Place.Id("zzcl_hill"), com.histmersion.api.catalogue.Place.NameAdd(com.histmersion.api.catalogue.Place.NameAdd.Name("Zzcl Höyük"), Some(com.histmersion.api.catalogue.Place.Name.Lang("tr"))))
        .map(_.added),
      Right(true)
    )
    assertEquals(c.manualNames(Some(com.histmersion.domain.catalogue.Place.Id.assume(" zzcl_hill "))).map(_.name), List("Zzcl Höyük"))
    val set = c.setOverride(
      Caller.Local,
      com.histmersion.domain.catalogue.Place.Id("zzcl_hill"),
      com.histmersion.api.catalogue.Place.OverrideSet(kind = Some(com.histmersion.api.catalogue.Place.OverrideSet.Kind("region")), reason = Some(com.histmersion.api.catalogue.Place.OverrideSet.Reason("dug")))
    )
    assertEquals(set.map(_.kindAfter), Right("region"))
    assertEquals(c.overrides(None).map(_.placeId), List("zzcl_hill"))
    assert(c.shapes(Api.PlacesGeoQuery(pick = Some(com.histmersion.api.GeoPick.Wire("nonsense")))).left.exists(_.message.value.contains("unknown pick")))
    assert(c.shapes(Api.PlacesGeoQuery(kind = Some(com.histmersion.api.catalogue.Place.Kind.Wire("region")))).isRight)
    assertEquals(c.geocode(com.histmersion.api.catalogue.Geocode.Attempt.Name("Zzcl"), Geocode.KindHint.Unknown, None).map(_.placeId), Some("zzcl_hill"))
    assertEquals(c.geocode(com.histmersion.api.catalogue.Geocode.Attempt.Name("nothing"), Geocode.KindHint.Unknown, None), None)

  test("a held lock refuses every write"):
    val gate = WriteGate()
    val (c, places) = controllers(gate)
    val held = gate.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore")) {
      Right(
        List(
          c.importPlaces(Caller.Local, None, None, doc).isLeft,
          c.addName(Caller.Local, com.histmersion.domain.catalogue.Place.Id("zzcl_hill"), com.histmersion.api.catalogue.Place.NameAdd(com.histmersion.api.catalogue.Place.NameAdd.Name("x"))).isLeft,
          c.removeName(Caller.Local, com.histmersion.domain.catalogue.Place.Id("zzcl_hill"), com.histmersion.api.catalogue.Place.NameRemove(com.histmersion.api.catalogue.Place.NameRemove.Name("x"))).isLeft,
          c.setOverride(Caller.Local, com.histmersion.domain.catalogue.Place.Id("zzcl_hill"), com.histmersion.api.catalogue.Place.OverrideSet(reset = com.histmersion.api.catalogue.Place.OverrideSet.Reset(true)))
            .isLeft,
          c.merge(Caller.Local, com.histmersion.domain.catalogue.Place.Id("zzcl_hill"), com.histmersion.api.catalogue.Place.MergeIn(com.histmersion.api.catalogue.Place.Id("zzcl_land"))).isLeft,
          c.remove(Caller.Local, com.histmersion.domain.catalogue.Place.Id("zzcl_hill"), com.histmersion.api.catalogue.Place.RemoveIn()).isLeft,
          c.add(Caller.Local, com.histmersion.api.catalogue.Place.Add(com.histmersion.api.catalogue.Place.Add.Label("Zzcl"), com.histmersion.api.catalogue.Place.Add.Kind(Place.Kind.Site), com.histmersion.api.catalogue.Longitude(0), com.histmersion.api.catalogue.Latitude(0), com.histmersion.api.catalogue.Place.Add.Source("fixture"))).isLeft,
          c.rename(Caller.Local, com.histmersion.domain.catalogue.Place.Id("zzcl_hill"), _ => Right(com.histmersion.domain.catalogue.Place.Id("x"))).isLeft,
          c.rebuild(Caller.Local).isLeft
        )
      )
    }
    assertEquals(held, Right(List.fill(9)(true)))
    assertEquals(places.count(), 0)
