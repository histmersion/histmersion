package com.histmersion.plugins.gazetteers

import com.histmersion.plugin.testkit.{HttpMode, PluginHarness, StubAnswer}

/** Histmersion's own network gazetteers run as any plugin author runs theirs: through the plugin testkit, which loads a
  * plugin from the class path as the server does and runs its source's download through the server's gazetteer
  * client, answered from canned answers — never the network.
  */
class GazetteerPluginsKitSuite extends munit.FunSuite:

  private def fixture(name: String): Array[Byte] =
    scala.io.Source.fromResource(s"gazetteers/$name")(using scala.io.Codec.UTF8).mkString.getBytes("UTF-8")

  private def load(id: String, stubs: Map[String, StubAnswer] = Map.empty) =
    PluginHarness.load(id, http = HttpMode.Stub(stubs), env = Map.empty).fold(e => fail(e), identity)

  private val plugins =
    List("gazetteer-cliopatria" -> "cliopatria", "gazetteer-awmc" -> "awmc", "gazetteer-chronontology" -> "chronontology")

  test("each plugin loads from the class path with its defaults and registers its one source"):
    plugins.foreach { (id, source) =>
      val h = load(id)
      assertEquals(h.gazetteerSources, List(source), id)
      assertEquals(h.plugin.info.version, "1.0.0", id)
      assertEquals(h.requests, Nil, s"$id asked an upstream while registering")
    }

  test("a gazetteer plugin has no setting of its own: anything in its block is refused"):
    val refused = PluginHarness.load(
      "gazetteer-awmc",
      "url = \"https://elsewhere.example\"",
      http = HttpMode.Offline,
      env = Map.empty
    )
    assert(refused.left.exists(_.contains("unknown setting")), refused.toString)

  test("awmc and chronontology: one request to the dataset, every place kept with the dataset's credit"):
    val awmc = load("gazetteer-awmc", Map(Awmc.url -> StubAnswer.ok(fixture("awmc.geojson"))))
    val r = awmc.readGazetteer("awmc")
    assertEquals((r.failure, r.refused), (None, Nil))
    assertEquals(r.requests, List(s"GET ${Awmc.url}"))
    assertEquals(r.accepted.map(_.place.id), List("awmc_pleiades_511444", "awmc_pleiades_511405", "awmc_pleiades_511171"))
    assertEquals(r.accepted.map(_.source).distinct, List(Some("Ancient World Mapping Center (UNC), ODbL")))
    val chrono =
      load("gazetteer-chronontology", Map(ChronOntology.url -> StubAnswer.ok(fixture("chronontology.json"))))
    val c = chrono.readGazetteer("chronontology")
    assertEquals(c.accepted.size, 3)
    assertEquals(c.accepted.map(_.source).distinct, List(Some("ChronOntology (DAI), open access")))

  test("cliopatria: a zip read, the parents first, an error answer named"):
    val zip =
      val out = java.io.ByteArrayOutputStream()
      val z = java.util.zip.ZipOutputStream(out)
      z.putNextEntry(java.util.zip.ZipEntry("cliopatria.geojson"))
      z.write(fixture("cliopatria.geojson"))
      z.closeEntry()
      z.close()
      out.toByteArray
    val r = load("gazetteer-cliopatria", Map(Cliopatria.url -> StubAnswer.ok(zip))).readGazetteer("cliopatria")
    assertEquals(r.failure, None)
    assertEquals(r.accepted.size, 6)
    assertEquals(r.accepted.take(3).forall(_.place.geometry.isEmpty), true)
    assertEquals(r.accepted.map(_.source).distinct, List(Some("Cliopatria (Seshat Databank), CC BY 4.0")))
    val missing = load("gazetteer-cliopatria", Map(Cliopatria.url -> StubAnswer(404, Array.emptyByteArray)))
    assertEquals(missing.readGazetteer("cliopatria").failure, Some(s"HTTP 404 from ${Cliopatria.url}"))
    assertEquals(
      missing.readGazetteer("nowhere").failure,
      Some("the plugin registered no gazetteer source 'nowhere'")
    )
