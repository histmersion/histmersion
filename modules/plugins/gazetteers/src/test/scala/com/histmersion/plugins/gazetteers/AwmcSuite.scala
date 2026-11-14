package com.histmersion
package plugins.gazetteers

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.*
import com.histmersion.plugin.testkit.PluginHarness
import com.histmersion.plugin.testkit.HttpMode

class AwmcSuite extends munit.FunSuite:

  private val fixture =
    DownloadedGazetteer.Text(
      scala.io.Source.fromResource("gazetteers/awmc.geojson")(using scala.io.Codec.UTF8).mkString
    )

  private def period(a: Int, b: Int): Option[Period] = Period.from(Year(a), Year(b)).toOption

  test("transform parses the fixture successfully"):
    val result = Awmc.read(fixture)
    assert(result.isRight, result.left.getOrElse(""))
    assertEquals(result.toOption.get.length, 3)

  test("IDs use Pleiades PID when available"):
    val places = Awmc.read(fixture).toOption.get
    assertEquals(places(0).id, "awmc_pleiades_511444")
    assertEquals(places(1).id, "awmc_pleiades_511405")

  test("labels come from TITLE"):
    val places = Awmc.read(fixture).toOption.get
    assertEquals(places(0).label, "Thynias")
    assertEquals(places(2).label, "Asticus M.")

  test("kind maps from P_TYPE"):
    val places = Awmc.read(fixture).toOption.get
    assertEquals(places(0).kind, "region")
    assertEquals(places(2).kind, "feature")

  test("names are populated"):
    val places = Awmc.read(fixture).toOption.get
    assertEquals(places(0).names.length, 1)
    assertEquals(places(0).names.head.name, "Thynias")

  test("geometry is preserved"):
    val places = Awmc.read(fixture).toOption.get
    assert(places(0).geometry.isDefined)
    assert(places(0).geometry.get.value.contains("MultiLineString"))

  // the host stores the dataset's credit with every place that states no source of its own
  test("source is set"):
    val places = Awmc.read(fixture).toOption.get
    assert(Awmc.rights.credit.value.contains("Ancient World Mapping Center"))
    assertEquals(places(0).source, None)

  test("properties contain pleiadesId and awmcType"):
    val places = Awmc.read(fixture).toOption.get
    val props = places(0).properties.get
    assert(props.value.contains("pleiadesId"), props)
    assert(props.value.contains("511444"), props)
    assert(props.value.contains("awmcType"), props)

  test("notes stored in properties"):
    val places = Awmc.read(fixture).toOption.get
    val props = places(2).properties.get
    assert(props.value.contains("notes"), props)

  test("transform is idempotent"):
    val r1 = Awmc.read(fixture).toOption.get
    val r2 = Awmc.read(fixture).toOption.get
    assertEquals(r1.map(_.id), r2.map(_.id))

  test("transform returns Left for invalid JSON"):
    assert(Awmc.read(DownloadedGazetteer.Text("not json")).isLeft)

  test("transform returns Left for empty features"):
    val empty = """{"type":"FeatureCollection","features":[]}"""
    val result = Awmc.read(DownloadedGazetteer.Text(empty))
    assert(result.isRight)
    assertEquals(result.toOption.get.length, 0)

  test("features without TITLE are skipped"):
    val noTitle = """{
      "type": "FeatureCollection",
      "features": [{"type":"Feature","properties":{"TYPE":"region"},"geometry":null}]
    }"""
    assertEquals(Awmc.read(DownloadedGazetteer.Text(noTitle)).toOption.get.length, 0)

  test("the gazetteer-awmc plugin registers the source awmc"):
    val h = PluginHarness
      .load(Plugin.Info.Id("gazetteer-awmc"), http = HttpMode.Offline, env = Map.empty)
      .fold(e => fail(e.sentence), identity)
    assertEquals(h.gazetteerSources, List("awmc"))
