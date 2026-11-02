package com.histmersion.domain.catalogue.places

import com.histmersion.server.ServerPlugins

import com.histmersion.plugin.*
import org.ekrich.config.ConfigFactory

import scala.collection.immutable.ListMap

/** The names `places fetch` and `init.fetch-sources` take: the first-party gazetteers in today's order, then a
  * plugin's; a first-party one switched off answers so, naming its switch; any other name is unknown.
  */
class BuiltinGazetteersSuite extends munit.FunSuite:

  private object Elsewhere extends GazetteerSource:
    def title = "Elsewhere atlas"
    def rights = GazetteerRights(Licence("CC0-1.0"), "Elsewhere atlas")
    def fetch(http: Plugin.Http) = Right(Nil)

  test("the stock generation registers the three first-party sources, listed in today's order"):
    val registered = ServerPlugins.offline.registries.gazetteerSources
    assertEquals(registered.keys.toSet, Set("cliopatria", "awmc", "chronontology"))
    assertEquals(BuiltinGazetteers.available(registered), List("cliopatria", "awmc", "chronontology"))
    assertEquals(BuiltinGazetteers.thirdParty(registered), Nil)
    assertEquals(
      BuiltinGazetteers.resolve("awmc", registered, "unknown").map(_.title),
      Right(
        "AWMC classical-world regional names and features"
      )
    )

  test("a source switched off is refused, naming the switch; an unknown one gets the caller's message"):
    val off = ServerPlugins
      .offlineWith(Nil, ConfigFactory.parseString("histmersion.plugins.gazetteer-awmc.enabled = false"))
      .registries
      .gazetteerSources
    assertEquals(off.keys.toSet, Set("cliopatria", "chronontology"))
    assertEquals(
      BuiltinGazetteers.resolve("awmc", off, "unknown"),
      Left(
        "AWMC classical-world regional names and features (awmc) is switched off " +
          "(histmersion.plugins.gazetteer-awmc.enabled = false)"
      )
    )
    assertEquals(BuiltinGazetteers.resolve("nowhere", off, "unknown 'nowhere'"), Left("unknown 'nowhere'"))
    assertEquals(BuiltinGazetteers.available(off), List("cliopatria", "awmc", "chronontology"))

  test("a plugin's source is listed after the first-party ones and found by its name"):
    val registered = ListMap[String, GazetteerSource]("elsewhere" -> Elsewhere, "cliopatria" -> Elsewhere)
    assertEquals(BuiltinGazetteers.available(registered), List("cliopatria", "awmc", "chronontology", "elsewhere"))
    assertEquals(BuiltinGazetteers.resolve("elsewhere", registered, "unknown"), Right(Elsewhere))
