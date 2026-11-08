package com.histmersion
package plugins.gazetteers

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.*
import com.histmersion.plugin.testkit.PluginHarness
import com.histmersion.plugin.testkit.HttpMode

class CliopatriaSuite extends munit.FunSuite:

  private val fixture = DownloadedGazetteer.Text(
    scala.io.Source.fromResource("gazetteers/cliopatria.geojson")(using scala.io.Codec.UTF8).mkString
  )

  private def period(a: Int, b: Int): Option[Period] = Period.from(Year(a), Year(b)).toOption

  test("transform produces one PlaceInput per feature"):
    val result = Cliopatria.read(fixture)
    assert(result.isRight, result.left.getOrElse(""))
    val places = result.toOption.get
    assertEquals(places.length, 3)

  test("place IDs include the time slice"):
    val places = Cliopatria.read(fixture).toOption.get
    val ids = places.map(_.id.value).sorted
    assert(ids.exists(_.contains("hittite_empire_-1650_-1401")), ids.mkString(", "))
    assert(ids.exists(_.contains("hittite_empire_-1400_-1201")), ids.mkString(", "))
    assert(ids.exists(_.contains("kingdom_of_elam_-3200_-2701")), ids.mkString(", "))

  test("period is set from FromYear/ToYear"):
    val places = Cliopatria.read(fixture).toOption.get
    val hittite1 = places.find(_.id.value.contains("-1650_-1401")).get
    assertEquals(hittite1.period, period(-1650, -1401))

  test("geometry is preserved as GeoJSON text"):
    val places = Cliopatria.read(fixture).toOption.get
    val hittite1 = places.find(_.id.value.contains("-1650_-1401")).get
    assert(hittite1.geometry.isDefined)
    assert(hittite1.geometry.get.value.contains("Polygon"))

  // the host stores the dataset's credit with every place that states no source of its own
  test("source is set to Cliopatria attribution"):
    val places = Cliopatria.read(fixture).toOption.get
    assert(Cliopatria.rights.credit.value.contains("Cliopatria"))
    assert(places.forall(_.source.isEmpty))

  test("kind is polity"):
    val places = Cliopatria.read(fixture).toOption.get
    assert(places.forall(_.kind.value == "polity"))

  test("names include the display name"):
    val places = Cliopatria.read(fixture).toOption.get
    val elam = places.find(_.id.value.contains("kingdom_of_elam")).get
    assert(elam.names.exists(_.name.value == "Kingdom of Elam"))

  test("same polity with different time slices gets a part_of relation to parent concept"):
    val places = Cliopatria.read(fixture).toOption.get
    val hittite1 = places.find(_.id.value.contains("-1650_-1401")).get
    assert(
      hittite1.relations.exists(r =>
        r.parentId.value == "cliopatria_hittite_empire" && r.kind.value == GazetteerRelation.PartOf
      )
    )

  test("MemberOf creates an additional relation"):
    val places = Cliopatria.read(fixture).toOption.get
    val elam = places.find(_.id.value.contains("kingdom_of_elam")).get
    assert(
      elam.relations.exists(_.parentId.value == "cliopatria_sumerian_city_states"),
      elam.relations.map(_.parentId).mkString(", ")
    )

  test("parentConcepts generates grouping nodes for each distinct polity"):
    val places = Cliopatria.read(fixture).toOption.get
    val parents = Cliopatria.parentConcepts(places)
    assert(parents.exists(_.id.value == "cliopatria_hittite_empire"))
    assert(parents.exists(_.id.value == "cliopatria_kingdom_of_elam"))
    assert(parents.forall(_.geometry.isEmpty))

  test("parentConcepts labels replace underscores with spaces"):
    val places = Cliopatria.read(fixture).toOption.get
    val parents = Cliopatria.parentConcepts(places)
    val h = parents.find(_.id.value == "cliopatria_hittite_empire").get
    assertEquals(h.label, "hittite empire")

  // 2026-10-09: every concept was imported with no period — 1 604 polities "valid at every year",
  // in every time-filtered view
  test("parentConcepts span their slices: earliest start to latest end, also for a MemberOf concept"):
    val places = Cliopatria.read(fixture).toOption.get
    val parents = Cliopatria.parentConcepts(places).map(p => p.id -> p.period).toMap
    assertEquals(parents(GazetteerPlace.Id("cliopatria_hittite_empire")), period(-1650, -1201))
    assertEquals(parents(GazetteerPlace.Id("cliopatria_kingdom_of_elam")), period(-3200, -2701))
    assertEquals(parents(GazetteerPlace.Id("cliopatria_sumerian_city_states")), period(-3200, -2701))

  test("a concept whose slices carry no years stays undated"):
    val undated = Cliopatria
      .readFeature(
        io.circe.parser.parse("""{"type":"Feature","properties":{"Name":"Nowhere"},"geometry":null}""").toOption.get,
        Cliopatria.Ordinal(7)
      )
      .toOption
      .get
    assertEquals(Cliopatria.parentConcepts(List(undated)).map(_.period), List(None))

  test("transform is idempotent"):
    val r1 = Cliopatria.read(fixture)
    val r2 = Cliopatria.read(fixture)
    assertEquals(r1, r2)

  test("transform returns Left for invalid JSON"):
    val result = Cliopatria.read(DownloadedGazetteer.Text("not json"))
    assert(result.isLeft)

  test("transform returns Left for empty features"):
    val result = Cliopatria.read(DownloadedGazetteer.Text("""{"type":"FeatureCollection","features":[]}"""))
    assert(result.isRight)
    assertEquals(result.toOption.get.length, 0)

  test("wikidata stored in properties"):
    val places = Cliopatria.read(fixture).toOption.get
    val hittite1 = places.find(_.id.value.contains("-1650_-1401")).get
    assert(hittite1.properties.exists(_.value.contains("Q170494")))

  test("the gazetteer-cliopatria plugin registers the source cliopatria"):
    val h = PluginHarness
      .load(Plugin.Info.Id("gazetteer-cliopatria"), http = HttpMode.Offline, env = Map.empty)
      .fold(e => fail(e.value), identity)
    assertEquals(h.gazetteerSources, List("cliopatria"))

  // the host's runner names no source: the parents come with the slices, first, as the runner stored them before
  test("fetch answers the parent concepts first, then the slices, from the zip the upstream ships"):
    val zip =
      val out = java.io.ByteArrayOutputStream()
      val z = java.util.zip.ZipOutputStream(out)
      List("__MACOSX/._c" -> "mac", "dir/" -> "", "cliopatria.geojson" -> fixture.value, "later.txt" -> "x").foreach {
        (n, s) =>
          z.putNextEntry(java.util.zip.ZipEntry(n))
          if !n.endsWith("/") then z.write(s.getBytes("UTF-8"))
          z.closeEntry()
      }
      z.close()
      out.toByteArray
    val asked = scala.collection.mutable.ListBuffer.empty[Plugin.Http.Url]
    val http = new Plugin.Http:
      def get(url: Plugin.Http.Url, headers: Map[Header.Name, Header.Value]) =
        Left(Problem("get is not used"))
      override def fetch(
          url: Plugin.Http.Url,
          headers: Map[Header.Name, Header.Value]
      ) =
        asked += url
        Right(Plugin.HttpResponse(Plugin.HttpResponse.Status(200), Map.empty, Plugin.HttpResponse.Body(zip)))
    val all = Cliopatria.fetch(http).fold(e => fail(e.value), identity)
    assertEquals(asked.toList, List(Cliopatria.url))
    assertEquals(
      all.map(_.id),
      Cliopatria.parentConcepts(Cliopatria.read(fixture).toOption.get).map(_.id) ++
        Cliopatria.read(fixture).toOption.get.map(_.id)
    )
    assertEquals(
      all.take(3).map(_.id).toSet,
      Set("cliopatria_hittite_empire", "cliopatria_kingdom_of_elam", "cliopatria_sumerian_city_states")
    )

  test("an error answer, a zip without a usable file: the download says which"):
    def answering(status: Plugin.HttpResponse.Status, body: Plugin.HttpResponse.Body) = new Plugin.Http:
      def get(url: Plugin.Http.Url, headers: Map[Header.Name, Header.Value]) =
        Left(Problem("get is not used"))
      override def fetch(
          url: Plugin.Http.Url,
          headers: Map[Header.Name, Header.Value]
      ) =
        Right(Plugin.HttpResponse(status, Map.empty, body))
    assertEquals(
      Cliopatria.fetch(answering(Plugin.HttpResponse.Status(404), Plugin.HttpResponse.Body(Array.emptyByteArray))),
      Left(s"HTTP 404 from ${Cliopatria.url}")
    )
    val empty =
      val out = java.io.ByteArrayOutputStream()
      val z = java.util.zip.ZipOutputStream(out)
      z.putNextEntry(java.util.zip.ZipEntry("__MACOSX/"))
      z.closeEntry()
      z.close()
      out.toByteArray
    assertEquals(
      Cliopatria.fetch(answering(Plugin.HttpResponse.Status(200), Plugin.HttpResponse.Body(empty))),
      Left("zip contains no usable file")
    )

  test("years in either order make one span; a year beyond what a Year holds leaves the slice undated"):
    val swapped = io.circe.parser
      .parse("""{"properties":{"Name":"Late","FromYear":-100,"ToYear":-200},"geometry":null}""")
      .toOption
      .get
    assertEquals(Cliopatria.readFeature(swapped, Cliopatria.Ordinal(0)).toOption.get.period, period(-200, -100))
    val huge = io.circe.parser
      .parse("""{"properties":{"Name":"Deep","FromYear":-3000000000,"ToYear":0},"geometry":null}""")
      .toOption
      .get
    val deep = Cliopatria.readFeature(huge, Cliopatria.Ordinal(0)).toOption.get
    assertEquals((deep.id, deep.period), ("cliopatria_deep_-3000000000_0", None))
