package com.histmersion.domain.library.acquisition

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.plugins.{PluginSetting, PluginStatus}
import com.histmersion.plugin.Access
import com.histmersion.pluginhost.FetchChain

/** The legal chain's generation, which makes the acquisition list due for a re-check again: a legal fetcher or a
  * fetcher plugin's setting changes it; a fetcher the config marks unauthorised never does. Pure.
  */
class FetcherGenerationSuite extends munit.FunSuite:

  private val plan = List(FetchChain.Step.Id("unpaywall") -> Access.Open, FetchChain.Step.Id("core") -> Access.Open)
  private def core(key: PluginSetting.Value) = PluginStatus(
    com.histmersion.api.plugins.PluginStatus.Id("oa-core"),
    com.histmersion.api.plugins.PluginStatus.Version("1.0"),
    com.histmersion.api.plugins.PluginStatus.SpiVersion(2),
    enabled = com.histmersion.api.plugins.PluginStatus.Enabled(true),
    com.histmersion.api.plugins.PluginStatus.State("registered"),
    provides = List(com.histmersion.api.plugins.PluginDescription.Provides("source-fetcher:core")),
    settings = List(PluginSetting(com.histmersion.api.plugins.PluginSetting.Key("api-key"), key))
  )
  private val unpaywall = PluginStatus(
    com.histmersion.api.plugins.PluginStatus.Id("oa-unpaywall"),
    com.histmersion.api.plugins.PluginStatus.Version("1.0"),
    com.histmersion.api.plugins.PluginStatus.SpiVersion(2),
    enabled = com.histmersion.api.plugins.PluginStatus.Enabled(true),
    com.histmersion.api.plugins.PluginStatus.State("registered"),
    provides = List(com.histmersion.api.plugins.PluginDescription.Provides("source-fetcher:unpaywall")),
    settings = List(PluginSetting(com.histmersion.api.plugins.PluginSetting.Key("contact-email"), com.histmersion.api.plugins.PluginSetting.Value("null")))
  )
  private val vault =
    PluginStatus(com.histmersion.api.plugins.PluginStatus.Id("keepassxc"), com.histmersion.api.plugins.PluginStatus.Version("0.1"), com.histmersion.api.plugins.PluginStatus.SpiVersion(2), enabled = com.histmersion.api.plugins.PluginStatus.Enabled(true), com.histmersion.api.plugins.PluginStatus.State("registered"), provides = List(com.histmersion.api.plugins.PluginDescription.Provides("secret-vault:keepassxc")))
  private def gen(
      p: List[(FetchChain.Step.Id, Access)] = plan,
      ps: List[PluginStatus] = List(unpaywall, core(PluginSetting.Value("null")), vault),
      contact: Boolean = true,
      coreKey: Boolean = false
  ) = FetcherGeneration.of(p, ps, contact, coreKey)

  test("the same chain and settings: the same generation, whatever order the report lists the plugins in"):
    assertEquals(gen(), gen(ps = List(vault, core(PluginSetting.Value("null")), unpaywall)))

  test("a new legal fetcher, a key, a contact e-mail or a plugin's new version: a new generation"):
    val g = gen()
    assert(gen(p = plan :+ (FetchChain.Step.Id("landing") -> Access.Open)) != g)
    assert(gen(ps = List(unpaywall, core(PluginSetting.Value("env:CORE_KEY")), vault)) != g)
    assert(gen(coreKey = true) != g)
    assert(gen(contact = false) != g)
    assert(gen(ps = List(unpaywall.modify(_.version).setTo(PluginStatus.Version("1.1")), core(PluginSetting.Value("null")), vault)) != g)

  test("a fetcher the config marks unauthorised, and a plugin that registers no legal fetcher, change nothing"):
    val g = gen()
    assertEquals(gen(p = plan :+ (FetchChain.Step.Id("stub") -> Access.Unauthorised)), g)
    val stubPlugin = PluginStatus(
      com.histmersion.api.plugins.PluginStatus.Id("stub"),
      com.histmersion.api.plugins.PluginStatus.Version("9"),
      com.histmersion.api.plugins.PluginStatus.SpiVersion(2),
      enabled = com.histmersion.api.plugins.PluginStatus.Enabled(true),
      com.histmersion.api.plugins.PluginStatus.State("registered"),
      provides = List(com.histmersion.api.plugins.PluginDescription.Provides("source-fetcher:stub")),
      settings = List(PluginSetting(com.histmersion.api.plugins.PluginSetting.Key("token"), com.histmersion.api.plugins.PluginSetting.Value("env:X")))
    )
    assertEquals(
      gen(p = plan :+ (FetchChain.Step.Id("stub") -> Access.Unauthorised), ps = List(unpaywall, core(PluginSetting.Value("null")), vault, stubPlugin)),
      g
    )
    assertEquals(gen(ps = List(unpaywall, core(PluginSetting.Value("null")), vault.modify(_.version).setTo(PluginStatus.Version("0.2")))), g)
    assert(FetcherGeneration.parts(plan, Nil, true, false).head.value == "unpaywall:open")
