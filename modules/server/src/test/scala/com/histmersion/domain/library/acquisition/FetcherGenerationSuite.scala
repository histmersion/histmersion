package com.histmersion.domain.library.acquisition

import com.histmersion.api.{PluginSetting, PluginStatus}
import com.histmersion.plugin.Access

/** The legal chain's generation, which makes the acquisition list due for a re-check again: a legal fetcher or a
  * fetcher plugin's setting changes it; a fetcher the config marks unauthorised never does. Pure.
  */
class FetcherGenerationSuite extends munit.FunSuite:

  private val plan = List("unpaywall" -> Access.Open, "core" -> Access.Open)
  private def core(key: String) = PluginStatus(
    "oa-core",
    "1.0",
    2,
    enabled = true,
    "registered",
    provides = List("source-fetcher:core"),
    settings = List(PluginSetting("api-key", key))
  )
  private val unpaywall = PluginStatus(
    "oa-unpaywall",
    "1.0",
    2,
    enabled = true,
    "registered",
    provides = List("source-fetcher:unpaywall"),
    settings = List(PluginSetting("contact-email", "null"))
  )
  private val vault =
    PluginStatus("keepassxc", "0.1", 2, enabled = true, "registered", provides = List("secret-vault:keepassxc"))
  private def gen(
      p: List[(String, Access)] = plan,
      ps: List[PluginStatus] = List(unpaywall, core("null"), vault),
      contact: Boolean = true,
      coreKey: Boolean = false
  ) = FetcherGeneration.of(p, ps, contact, coreKey)

  test("the same chain and settings: the same generation, whatever order the report lists the plugins in"):
    assertEquals(gen(), gen(ps = List(vault, core("null"), unpaywall)))

  test("a new legal fetcher, a key, a contact e-mail or a plugin's new version: a new generation"):
    val g = gen()
    assert(gen(p = plan :+ ("landing" -> Access.Open)) != g)
    assert(gen(ps = List(unpaywall, core("env:CORE_KEY"), vault)) != g)
    assert(gen(coreKey = true) != g)
    assert(gen(contact = false) != g)
    assert(gen(ps = List(unpaywall.copy(version = "1.1"), core("null"), vault)) != g)

  test("a fetcher the config marks unauthorised, and a plugin that registers no legal fetcher, change nothing"):
    val g = gen()
    assertEquals(gen(p = plan :+ ("stub" -> Access.Unauthorised)), g)
    val stubPlugin = PluginStatus(
      "stub",
      "9",
      2,
      enabled = true,
      "registered",
      provides = List("source-fetcher:stub"),
      settings = List(PluginSetting("token", "env:X"))
    )
    assertEquals(
      gen(p = plan :+ ("stub" -> Access.Unauthorised), ps = List(unpaywall, core("null"), vault, stubPlugin)),
      g
    )
    assertEquals(gen(ps = List(unpaywall, core("null"), vault.copy(version = "0.2"))), g)
    assert(FetcherGeneration.parts(plan, Nil, true, false).head == "unpaywall:open")
