package com.histmersion
package plugins.events

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.{CatalogueQuery, Year}
import com.histmersion.plugin.testkit.{HttpMode, PluginHarness, StubAnswer}
import com.histmersion.pluginhost.HttpAnswer
import com.histmersion.plugin.Plugin
import com.histmersion.pluginhost.HttpTransport
import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.Registries
import com.histmersion.plugin.CatalogueEvent

/** Histmersion's own event catalogues run as any plugin author runs theirs: through the plugin testkit, which loads a
  * plugin from the class path as the server does (the host's own `histmersion.events` settings supplied to a
  * first-party plugin) and answers its requests from canned answers — never the network.
  */
class CataloguePluginsKitSuite extends munit.FunSuite:

  private def fixture(name: String): HttpAnswer.Text =
    HttpAnswer.Text(scala.io.Source.fromResource(s"events/$name")(using scala.io.Codec.UTF8).mkString)

  private def load(
      id: Plugin.Info.Id,
      stubs: Map[HttpTransport.Url, StubAnswer] = Map.empty,
      config: String = ""
  ) =
    PluginHarness.load(id, config, http = HttpMode.Stub(stubs), env = Map.empty).fold(e => fail(e.legacyText), identity)

  test("each plugin loads from the class path with its defaults and the host's settings, and registers its catalogue"):
    List("events-gvp" -> "gvp", "events-noaa" -> "noaa", "events-wikidata" -> "wikidata").foreach { (id, catalogue) =>
      val h = load(Plugin.Info.Id(id))
      assertEquals(h.contributions.eventCatalogues, List(catalogue), id)
      assertEquals(h.plugin.info.version, "0.1.0", id)
      assertEquals(h.requests, Nil, s"$id asked an upstream while registering")
    }

  test("a catalogue plugin has no setting the host owns: an endpoint in its block is refused"):
    val refused =
      PluginHarness.load(
        Plugin.Info.Id("events-gvp"),
        "url = \"https://elsewhere.example\"",
        http = HttpMode.Offline,
        env = Map.empty
      )
    assert(refused.left.exists(_.legacyText.contains("unknown setting")), refused.toString)

  test("events-gvp: a window of years at the host's endpoint, each record checked and dated as the server stores it"):
    val url = GvpCatalogue.url(
      GvpSettings.Url("https://webservices.volcano.si.edu/geoserver/GVP-VOTW/ows"),
      GvpCatalogue.Year(-2000),
      GvpCatalogue.Year(-1001),
      GvpSettings.PageSize(5000)
    )
    val h = load(
      Plugin.Info.Id("events-gvp"),
      Map(
        HttpTransport.Url(url.value) -> StubAnswer.text(fixture("gvp.json"), CoreQuota.HeaderValue("application/json"))
      )
    )
    val r = h.readCatalogue(Registries.EventCataloguesKey("gvp"), CatalogueQuery(Some(Year(-2000)), Year(-1001)))
    assertEquals(r.parts, List("years:-2000..-1001"))
    assertEquals(r.requests, List(s"GET $url"))
    assertEquals((r.failure, r.refused), (None, Nil))
    val thera = r.accepted.find(_.event.id == CatalogueEvent.Id("13879")).get
    assertEquals(
      (thera.notBefore.map(_.value.toLong), thera.notAfter.map(_.value.toLong)),
      (Some(-1624L), Some(-1596L))
    )

  test(
    "events-gvp: the host's own events.gvp keys reach the plugin (the user's endpoint, by its HISTMERSION_ variable)"
  ):
    val url = GvpCatalogue.url(
      GvpSettings.Url("https://gvp.example/ows"),
      GvpCatalogue.Year(-2000),
      GvpCatalogue.Year(-1001),
      GvpSettings.PageSize(5000)
    )
    val h = PluginHarness
      .load(
        Plugin.Info.Id("events-gvp"),
        http = HttpMode.Stub(
          Map(
            HttpTransport
              .Url(url.value) -> StubAnswer.text(fixture("gvp.json"), CoreQuota.HeaderValue("application/json"))
          )
        ),
        env = Map("HISTMERSION_EVENTS_GVP_URL" -> "https://gvp.example/ows")
      )
      .fold(e => fail(e.legacyText), identity)
    val r = h.readCatalogue(Registries.EventCataloguesKey("gvp"), CatalogueQuery(Some(Year(-2000)), Year(-1001)))
    assertEquals(r.requests, List(s"GET $url"))
    assertEquals(r.accepted.size, 3)
    assertEquals(
      h.readCatalogue(Registries.EventCataloguesKey("gvp"), CatalogueQuery(Some(Year(-3000)), Year(-1))).parts,
      List("years:-3000..-2001", "years:-2000..-1001", "years:-1000..-1")
    )

  test("events-noaa and events-wikidata: their parts from the host's settings, every NOAA dataset from page 1"):
    assertEquals(
      load(Plugin.Info.Id("events-noaa"))
        .readCatalogue(Registries.EventCataloguesKey("noaa"), CatalogueQuery(None, Year(-2000)))
        .parts,
      List("earthquakes:1", "volcanoes:1", "tsunamis:1")
    )
    val wd = PluginHarness
      .load(
        Plugin.Info.Id("events-wikidata"),
        http = HttpMode.Offline,
        env = Map("HISTMERSION_EVENTS_WIKIDATA_CLASSES" -> "Q188055:siege")
      )
      .fold(e => fail(e.legacyText), identity)
    assertEquals(
      wd.readCatalogue(Registries.EventCataloguesKey("wikidata"), CatalogueQuery(None, Year(-2000))).parts,
      List("class:Q188055")
    )
