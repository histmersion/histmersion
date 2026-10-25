package com.histmersion.server.importers

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.values.PlaceKind

class CliopatriaImporterSuite extends munit.FunSuite:

  private val fixture = """{
    "type": "FeatureCollection",
    "features": [
      {
        "type": "Feature",
        "properties": {
          "Name": "Hittite Empire",
          "FromYear": -1650,
          "ToYear": -1401,
          "Type": "POLITY",
          "Wikidata": "Q170494",
          "Wikipedia": "Hittites",
          "SeshatID": "tr_hittite_eg",
          "MemberOf": "",
          "Components": "",
          "Area": 50000.0
        },
        "geometry": {
          "type": "Polygon",
          "coordinates": [[[32.0,37.0],[36.0,37.0],[36.0,40.0],[32.0,40.0],[32.0,37.0]]]
        }
      },
      {
        "type": "Feature",
        "properties": {
          "Name": "Hittite Empire",
          "FromYear": -1400,
          "ToYear": -1201,
          "Type": "POLITY",
          "Wikidata": "Q170494",
          "Wikipedia": "Hittites",
          "SeshatID": "tr_hittite_nk",
          "MemberOf": "",
          "Components": "",
          "Area": 80000.0
        },
        "geometry": {
          "type": "Polygon",
          "coordinates": [[[30.0,35.0],[38.0,35.0],[38.0,41.0],[30.0,41.0],[30.0,35.0]]]
        }
      },
      {
        "type": "Feature",
        "properties": {
          "Name": "Kingdom of Elam",
          "FromYear": -3200,
          "ToYear": -2701,
          "Type": "POLITY",
          "Wikidata": "Q192736",
          "Wikipedia": "Elam",
          "SeshatID": "",
          "MemberOf": "Sumerian City-States",
          "Components": "",
          "Area": 30000.0
        },
        "geometry": {
          "type": "Polygon",
          "coordinates": [[[48.0,30.0],[52.0,30.0],[52.0,34.0],[48.0,34.0],[48.0,30.0]]]
        }
      }
    ]
  }"""

  test("transform produces one PlaceInput per feature"):
    val result = CliopatriaImporter.transform(fixture)
    assert(result.isRight, result.left.getOrElse(""))
    val places = result.toOption.get
    assertEquals(places.length, 3)

  test("place IDs include the time slice"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    val ids = places.map(_.place.id).sorted
    assert(ids.exists(_.contains("hittite_empire_-1650_-1401")), ids.mkString(", "))
    assert(ids.exists(_.contains("hittite_empire_-1400_-1201")), ids.mkString(", "))
    assert(ids.exists(_.contains("kingdom_of_elam_-3200_-2701")), ids.mkString(", "))

  test("period is set from FromYear/ToYear"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    val hittite1 = places.find(_.place.id.contains("-1650_-1401")).get
    assertEquals(hittite1.place.period, Some(Period(-1650, -1401)))

  test("geometry is preserved as GeoJSON text"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    val hittite1 = places.find(_.place.id.contains("-1650_-1401")).get
    assert(hittite1.place.geometry.isDefined)
    assert(hittite1.place.geometry.get.contains("Polygon"))

  test("source is set to Cliopatria attribution"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    assert(places.forall(_.place.source.exists(_.contains("Cliopatria"))))

  test("kind is polity"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    assert(places.forall(_.place.kind == PlaceKind.Polity))

  test("names include the display name"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    val elam = places.find(_.place.id.contains("kingdom_of_elam")).get
    assert(elam.names.exists(_.name == "Kingdom of Elam"))

  test("same polity with different time slices gets a part_of relation to parent concept"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    val hittite1 = places.find(_.place.id.contains("-1650_-1401")).get
    assert(hittite1.relations.exists(r =>
      r.parentId == "cliopatria_hittite_empire" && r.kind == PlaceRelation.PartOf))

  test("MemberOf creates an additional relation"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    val elam = places.find(_.place.id.contains("kingdom_of_elam")).get
    assert(elam.relations.exists(_.parentId == "cliopatria_sumerian_city_states"),
      elam.relations.map(_.parentId).mkString(", "))

  test("parentConcepts generates grouping nodes for each distinct polity"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    val parents = CliopatriaImporter.parentConcepts(places)
    assert(parents.exists(_.place.id == "cliopatria_hittite_empire"))
    assert(parents.exists(_.place.id == "cliopatria_kingdom_of_elam"))
    assert(parents.forall(_.place.geometry.isEmpty))

  test("parentConcepts labels replace underscores with spaces"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    val parents = CliopatriaImporter.parentConcepts(places)
    val h = parents.find(_.place.id == "cliopatria_hittite_empire").get
    assertEquals(h.place.label, "hittite empire")

  // 2026-10-09: every concept was imported with no period — 1 604 polities "valid at every year",
  // in every time-filtered view
  test("parentConcepts span their slices: earliest start to latest end, also for a MemberOf concept"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    val parents = CliopatriaImporter.parentConcepts(places).map(p => p.place.id -> p.place.period).toMap
    assertEquals(parents("cliopatria_hittite_empire"), Some(Period(-1650, -1201)))
    assertEquals(parents("cliopatria_kingdom_of_elam"), Some(Period(-3200, -2701)))
    assertEquals(parents("cliopatria_sumerian_city_states"), Some(Period(-3200, -2701)))

  test("a concept whose slices carry no years stays undated"):
    val undated = CliopatriaImporter.readFeature(io.circe.parser.parse(
      """{"type":"Feature","properties":{"Name":"Nowhere"},"geometry":null}""").toOption.get, 7).toOption.get
    assertEquals(CliopatriaImporter.parentConcepts(List(undated)).map(_.place.period), List(None))

  test("transform is idempotent"):
    val r1 = CliopatriaImporter.transform(fixture)
    val r2 = CliopatriaImporter.transform(fixture)
    assertEquals(r1, r2)

  test("transform returns Left for invalid JSON"):
    val result = CliopatriaImporter.transform("not json")
    assert(result.isLeft)

  test("transform returns Left for empty features"):
    val result = CliopatriaImporter.transform("""{"type":"FeatureCollection","features":[]}""")
    assert(result.isRight)
    assertEquals(result.toOption.get.length, 0)

  test("wikidata stored in properties"):
    val places = CliopatriaImporter.transform(fixture).toOption.get
    val hittite1 = places.find(_.place.id.contains("-1650_-1401")).get
    assert(hittite1.place.properties.exists(_.contains("Q170494")))

  test("GazetteerImporter.byName resolves cliopatria"):
    assertEquals(GazetteerImporter.byName("cliopatria"), Some(CliopatriaImporter))
