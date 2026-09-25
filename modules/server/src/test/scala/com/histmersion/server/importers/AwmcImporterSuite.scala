package com.histmersion.server.importers

import com.histmersion.core.*

class AwmcImporterSuite extends munit.FunSuite:

  private val fixture = """{
    "type": "FeatureCollection",
    "features": [
      {
        "type": "Feature",
        "properties": {
          "TITLE": "Thynias",
          "TYPE": "region",
          "PID": "511444",
          "CREATED": "1899/12/30",
          "P_TYPE": "region"
        },
        "geometry": {
          "type": "MultiLineString",
          "coordinates": [[[28.5,41.5],[29.0,42.0]]]
        }
      },
      {
        "type": "Feature",
        "properties": {
          "TITLE": "Salmydessos",
          "TYPE": "region",
          "PID": "511405",
          "P_TYPE": "region"
        },
        "geometry": {
          "type": "MultiLineString",
          "coordinates": [[[28.0,42.0],[28.5,42.5]]]
        }
      },
      {
        "type": "Feature",
        "properties": {
          "TITLE": "Asticus M.",
          "TYPE": "mountain",
          "PID": "511171",
          "P_TYPE": "mountain",
          "NOTES": "Should be 'field'"
        },
        "geometry": {
          "type": "MultiLineString",
          "coordinates": [[[28.2,41.8],[28.7,42.2]]]
        }
      }
    ]
  }"""

  test("transform parses the fixture successfully"):
    val result = AwmcImporter.transform(fixture)
    assert(result.isRight, result.left.getOrElse(""))
    assertEquals(result.toOption.get.length, 3)

  test("IDs use Pleiades PID when available"):
    val places = AwmcImporter.transform(fixture).toOption.get
    assertEquals(places(0).place.id, "awmc_pleiades_511444")
    assertEquals(places(1).place.id, "awmc_pleiades_511405")

  test("labels come from TITLE"):
    val places = AwmcImporter.transform(fixture).toOption.get
    assertEquals(places(0).place.label, "Thynias")
    assertEquals(places(2).place.label, "Asticus M.")

  test("kind maps from P_TYPE"):
    val places = AwmcImporter.transform(fixture).toOption.get
    assertEquals(places(0).place.kind, PlaceKind.Region)
    assertEquals(places(2).place.kind, PlaceKind.Feature)

  test("names are populated"):
    val places = AwmcImporter.transform(fixture).toOption.get
    assertEquals(places(0).names.length, 1)
    assertEquals(places(0).names.head.name, "Thynias")

  test("geometry is preserved"):
    val places = AwmcImporter.transform(fixture).toOption.get
    assert(places(0).place.geometry.isDefined)
    assert(places(0).place.geometry.get.contains("MultiLineString"))

  test("source is set"):
    val places = AwmcImporter.transform(fixture).toOption.get
    assert(places(0).place.source.get.contains("Ancient World Mapping Center"))

  test("properties contain pleiadesId and awmcType"):
    val places = AwmcImporter.transform(fixture).toOption.get
    val props = places(0).place.properties.get
    assert(props.contains("pleiadesId"), props)
    assert(props.contains("511444"), props)
    assert(props.contains("awmcType"), props)

  test("notes stored in properties"):
    val places = AwmcImporter.transform(fixture).toOption.get
    val props = places(2).place.properties.get
    assert(props.contains("notes"), props)

  test("transform is idempotent"):
    val r1 = AwmcImporter.transform(fixture).toOption.get
    val r2 = AwmcImporter.transform(fixture).toOption.get
    assertEquals(r1.map(_.place.id), r2.map(_.place.id))

  test("transform returns Left for invalid JSON"):
    assert(AwmcImporter.transform("not json").isLeft)

  test("transform returns Left for empty features"):
    val empty = """{"type":"FeatureCollection","features":[]}"""
    val result = AwmcImporter.transform(empty)
    assert(result.isRight)
    assertEquals(result.toOption.get.length, 0)

  test("features without TITLE are skipped"):
    val noTitle = """{
      "type": "FeatureCollection",
      "features": [{"type":"Feature","properties":{"TYPE":"region"},"geometry":null}]
    }"""
    assertEquals(AwmcImporter.transform(noTitle).toOption.get.length, 0)

  test("GazetteerImporter.byName resolves awmc"):
    assert(GazetteerImporter.byName("awmc").isDefined)
