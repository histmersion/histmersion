package com.histmersion.domain.catalogue.places

import com.histmersion.api.*
import com.histmersion.values.PlaceKind
import com.histmersion.config.AppConfig
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.identity.AliasKind

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference

/** The gazetteer's controllers over the in-memory records, no server and no database: an LPF document imported (a bad
  * kind or document refused whole), an old id answering as its place, the tree's configured depth, a person's names and
  * overrides parsed at the boundary, the map's pick checked, and every write refused while the maintenance lock is
  * held. The map's shapes, the geometry and the geocoder are mechanisms over PostGIS: here they answer what they were
  * asked.
  */
class PlacesControllersSuite extends munit.FunSuite:
  private val cfg = AppConfig.load()

  private val shapes: PlaceMap = (kind, time, limit, _) => s"$kind $time $limit"
  private val geometry: PlaceGeometry = new PlaceGeometry:
    def rebuildFindingPlaces(toleranceKm: Double) = PlaceRebuildResult(0, 0, 0, 0, 0, 0)
    def buildHierarchy() = SpatialHierarchyResult(0, 0, 0)
    def replaceLand(geometries: List[String]) = geometries.size
    def reclip() = 0
  private val geocoder: Geocoding = (name, _, _, _) =>
    Option.when(name == "Zzcl")(Geocode.Resolution(name, "zzcl_hill", "Zzclhill", 34.6, 40.0))

  private def controllers(gate: WriteGate = WriteGate()): (PlacesControllers, PlaceService) =
    val state = AtomicReference(CatalogueState.empty)
    val places = PlaceService.InMemory(state, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    val current: (AliasKind, String) => String = (_, id) => state.get.placeAliases.getOrElse(id, id)
    (PlacesControllers(places, shapes, geometry, geocoder, current, () => cfg, (l, d) => l.getOrElse(d), gate), places)

  private val doc =
    """{"type":"FeatureCollection","features":[
      |{"@id":"zzcl_land","type":"Feature","properties":{"title":"Zzclland"},
      | "geometry":{"type":"Polygon","coordinates":[[[30,36],[40,36],[40,42],[30,42],[30,36]]]}},
      |{"@id":"zzcl_hill","type":"Feature","properties":{"title":"Zzclhill"},
      | "names":[{"toponym":"Zzclhill","lang":"en"}],
      | "geometry":{"type":"Point","coordinates":[34.6,40.0]},
      | "relations":[{"relationType":"gvp:broaderPartitive","relationTo":"zzcl_land"}]}
      |]}""".stripMargin

  test("an LPF document imported, its places read back, an old id answering as its place; a bad import refused whole"):
    val (c, _) = controllers()
    val r = c.importPlaces(Caller.Local, Some("site"), Some("seed"), doc).fold(e => fail(e.message), identity)
    assertEquals((r.places, r.relations), (2, 1))
    assertEquals(c.search(Some("zzcl"), None, None).map(_.id).toSet, Set("zzcl_land", "zzcl_hill"))
    assertEquals(c.children("zzcl_land", None).map(_.map(_.id)), Right(List("zzcl_hill")))
    assertEquals(c.tree("zzcl_land", None, None).map(_.map(_.id)), Right(List("zzcl_land", "zzcl_hill")))
    assertEquals(c.rename(Caller.Local, "zzcl_hill", _ => Right("zzcl_mound")), Right(("zzcl_hill", "zzcl_mound")))
    assertEquals(c.get("zzcl_hill", None).map(_.place.id), Right("zzcl_mound"), "the old id answers")
    assertEquals(c.links("zzcl_none").left.map(_.message), Left("No place 'zzcl_none'"))
    assert(c.importPlaces(Caller.Local, Some("nonsense"), None, doc).left.exists(_.message.contains("unknown kind")))
    assert(c.importPlaces(Caller.Local, None, Some("other"), doc).left.exists(_.message.contains("seed or local")))
    assert(c.importPlaces(Caller.Local, None, None, "not json").isLeft)

  test("names and overrides parsed at the boundary; the map's pick checked; the geocoder asked"):
    val (c, _) = controllers()
    val _ = c.importPlaces(Caller.Local, Some("site"), None, doc)
    assert(c.addName(Caller.Local, "zzcl_hill", PlaceNameAdd("  ")).isLeft, "a blank name")
    assertEquals(c.addName(Caller.Local, "zzcl_hill", PlaceNameAdd("Zzcl Höyük", Some("tr"))).map(_.added), Right(true))
    assertEquals(c.manualNames(Some(" zzcl_hill ")).map(_.name), List("Zzcl Höyük"))
    val set = c.setOverride(Caller.Local, "zzcl_hill", PlaceOverrideSet(kind = Some("region"), reason = Some("dug")))
    assertEquals(set.map(_.kindAfter), Right("region"))
    assertEquals(c.overrides(None).map(_.placeId), List("zzcl_hill"))
    assert(c.shapes(Api.PlacesGeoQuery(pick = Some("nonsense"))).left.exists(_.message.contains("unknown pick")))
    assert(c.shapes(Api.PlacesGeoQuery(kind = Some("region"))).isRight)
    assertEquals(c.geocode("Zzcl", Geocode.KindHint.Unknown, None).map(_.placeId), Some("zzcl_hill"))
    assertEquals(c.geocode("nothing", Geocode.KindHint.Unknown, None), None)

  test("a held lock refuses every write"):
    val gate = WriteGate()
    val (c, places) = controllers(gate)
    val held = gate.exclusively("restore") {
      Right(
        List(
          c.importPlaces(Caller.Local, None, None, doc).isLeft,
          c.addName(Caller.Local, "zzcl_hill", PlaceNameAdd("x")).isLeft,
          c.removeName(Caller.Local, "zzcl_hill", PlaceNameRemove("x")).isLeft,
          c.setOverride(Caller.Local, "zzcl_hill", PlaceOverrideSet(reset = true)).isLeft,
          c.merge(Caller.Local, "zzcl_hill", PlaceMergeIn("zzcl_land")).isLeft,
          c.remove(Caller.Local, "zzcl_hill", PlaceRemoveIn()).isLeft,
          c.add(Caller.Local, PlaceAdd("Zzcl", PlaceKind.Site, 0, 0, "fixture")).isLeft,
          c.rename(Caller.Local, "zzcl_hill", _ => Right("x")).isLeft,
          c.rebuild(Caller.Local).isLeft
        )
      )
    }
    assertEquals(held, Right(List.fill(9)(true)))
    assertEquals(places.count(), 0)
