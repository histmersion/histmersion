package com.histmersion
package plugins.gazetteers

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.testkit.{HttpMode, PluginHarness, StubAnswer}
import com.histmersion.pluginhost.{HttpAnswer, HttpTransport, Registries}
import com.histmersion.testsupport.ComparedByValue.given

/** Histmersion's own network gazetteers run as any plugin author runs theirs: through the plugin testkit, which loads a
  * plugin from the class path as the server does and runs its source's download through the server's gazetteer client,
  * answered from canned answers — never the network.
  */
class GazetteerPluginsKitSuite extends munit.FunSuite:

  private def fixture(name: String): HttpAnswer.Body =
    HttpAnswer.Body(
      scala.io.Source.fromResource(s"gazetteers/$name")(using scala.io.Codec.UTF8).mkString.getBytes("UTF-8")
    )

  private def load(id: Plugin.Info.Id, stubs: Map[HttpTransport.Url, StubAnswer] = Map.empty) =
    PluginHarness
      .load(id, http = HttpMode.Stub(stubs), env = Map.empty)
      .fold(e => fail(e.sentences.mkString("; ")), identity)

  private val plugins =
    List(
      Plugin.Info.Id("gazetteer-cliopatria") -> "cliopatria",
      Plugin.Info.Id("gazetteer-awmc") -> "awmc",
      Plugin.Info.Id("gazetteer-chronontology") -> "chronontology"
    )

  test("each plugin loads from the class path with its defaults and registers its one source"):
    plugins.foreach { (id, source) =>
      val h = load(id)
      assertEquals(h.gazetteerSources, List(source), id)
      assertEquals(h.plugin.info.version, "0.1.0", id)
      assertEquals(h.requests, Nil, s"$id asked an upstream while registering")
    }

  test("a gazetteer plugin has no setting of its own: anything in its block is refused"):
    val refused = PluginHarness.load(
      Plugin.Info.Id("gazetteer-awmc"),
      "url = \"https://elsewhere.example\"",
      http = HttpMode.Offline,
      env = Map.empty
    )
    assert(refused.left.exists(_.sentences.mkString("; ").contains("is no setting of the plugin")), refused.toString)

  test("awmc and chronontology: one request to the dataset, every place kept with the dataset's credit"):
    val awmc = load(
      Plugin.Info.Id("gazetteer-awmc"),
      Map(HttpTransport.Url(Awmc.url.value) -> StubAnswer.ok(fixture("awmc.geojson")))
    )
    val r = awmc.readGazetteer(Registries.GazetteerSourcesKey("awmc"))
    assertEquals((r.failure, r.refused), (None, Nil))
    assertEquals(r.requests, List(s"GET ${Awmc.url}"))
    assertEquals(
      r.accepted.map(_.place.id),
      List("awmc_pleiades_511444", "awmc_pleiades_511405", "awmc_pleiades_511171")
    )
    assertEquals(r.accepted.map(_.source).distinct, List(Some("Ancient World Mapping Center (UNC), ODbL")))
    val chrono =
      load(
        Plugin.Info.Id("gazetteer-chronontology"),
        Map(HttpTransport.Url(ChronOntology.url.value) -> StubAnswer.ok(fixture("chronontology.json")))
      )
    val c = chrono.readGazetteer(Registries.GazetteerSourcesKey("chronontology"))
    assertEquals(c.accepted.size, 3)
    assertEquals(c.accepted.map(_.source).distinct, List(Some("ChronOntology (DAI), open access")))

  test("cliopatria: a zip read, the parents first, an error answer named"):
    val zip =
      val out = java.io.ByteArrayOutputStream()
      val z = java.util.zip.ZipOutputStream(out)
      z.putNextEntry(java.util.zip.ZipEntry("cliopatria.geojson"))
      z.write(fixture("cliopatria.geojson").value)
      z.closeEntry()
      z.close()
      out.toByteArray
    val r = load(
      Plugin.Info.Id("gazetteer-cliopatria"),
      Map(HttpTransport.Url(Cliopatria.url.value) -> StubAnswer.ok(HttpAnswer.Body(zip)))
    ).readGazetteer(Registries.GazetteerSourcesKey("cliopatria"))
    assertEquals(r.failure, None)
    assertEquals(r.accepted.size, 6)
    assertEquals(r.accepted.take(3).forall(_.place.geometry.isEmpty), true)
    assertEquals(r.accepted.map(_.source).distinct, List(Some("Cliopatria (Seshat Databank), CC BY 4.0")))
    val missing = load(
      Plugin.Info.Id("gazetteer-cliopatria"),
      Map(
        HttpTransport.Url(Cliopatria.url.value) -> StubAnswer(
          HttpAnswer.Code(404),
          HttpAnswer.Body(Array.emptyByteArray)
        )
      )
    )
    assertEquals(
      missing.readGazetteer(Registries.GazetteerSourcesKey("cliopatria")).failure.map(_.sentence),
      Some(s"github.com answered HTTP 404 at ${Cliopatria.url} — ")
    )
    assertEquals(
      missing.readGazetteer(Registries.GazetteerSourcesKey("nowhere")).failure.map(_.sentence),
      Some("no GazetteerSource 'nowhere'")
    )
