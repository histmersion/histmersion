package com.histmersion
package domain.library.acquisition

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.plugins.{PluginSetting, PluginStatus}
import com.histmersion.api.plugins.PluginState
import com.histmersion.plugin.Access
import com.histmersion.pluginhost.FetchChain
import com.histmersion.api.plugins.PluginDescription
import com.histmersion.lint.{tolerated, LintRule}

/** The legal chain's generation, which makes the acquisition list due for a re-check again: a legal fetcher or a
  * fetcher plugin's setting changes it; a fetcher the config marks unauthorised never does. Pure.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
class FetcherGenerationSuite extends munit.FunSuite:

  private val plan = List(FetchChain.Step.Id("unpaywall") -> Access.Open, FetchChain.Step.Id("core") -> Access.Open)
  private def core(key: PluginSetting.Value) = PluginStatus(
    PluginStatus.Id("oa-core"),
    PluginStatus.Version("1.0"),
    PluginStatus.SpiVersion(2),
    enabled = PluginStatus.Enabled(true),
    PluginState.Wire("registered"),
    provides = List(PluginDescription.Provides("source-fetcher:core")),
    settings = List(PluginSetting(PluginSetting.Key("api-key"), key))
  )
  private val unpaywall = PluginStatus(
    PluginStatus.Id("oa-unpaywall"),
    PluginStatus.Version("1.0"),
    PluginStatus.SpiVersion(2),
    enabled = PluginStatus.Enabled(true),
    PluginState.Wire("registered"),
    provides = List(PluginDescription.Provides("source-fetcher:unpaywall")),
    settings = List(PluginSetting(PluginSetting.Key("contact-email"), PluginSetting.Value("null")))
  )
  private val vault =
    PluginStatus(
      PluginStatus.Id("keepassxc"),
      PluginStatus.Version("0.1"),
      PluginStatus.SpiVersion(2),
      enabled = PluginStatus.Enabled(true),
      PluginState.Wire("registered"),
      provides = List(PluginDescription.Provides("secret-vault:keepassxc"))
    )
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
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
    assert(
      gen(ps =
        List(unpaywall.modify(_.version).setTo(PluginStatus.Version("1.1")), core(PluginSetting.Value("null")), vault)
      ) != g
    )

  test("a fetcher the config marks unauthorised, and a plugin that registers no legal fetcher, change nothing"):
    val g = gen()
    assertEquals(gen(p = plan :+ (FetchChain.Step.Id("stub") -> Access.Unauthorised)), g)
    val stubPlugin = PluginStatus(
      PluginStatus.Id("stub"),
      PluginStatus.Version("9"),
      PluginStatus.SpiVersion(2),
      enabled = PluginStatus.Enabled(true),
      PluginState.Wire("registered"),
      provides = List(PluginDescription.Provides("source-fetcher:stub")),
      settings = List(PluginSetting(PluginSetting.Key("token"), PluginSetting.Value("env:X")))
    )
    assertEquals(
      gen(
        p = plan :+ (FetchChain.Step.Id("stub") -> Access.Unauthorised),
        ps = List(unpaywall, core(PluginSetting.Value("null")), vault, stubPlugin)
      ),
      g
    )
    assertEquals(
      gen(ps =
        List(unpaywall, core(PluginSetting.Value("null")), vault.modify(_.version).setTo(PluginStatus.Version("0.2")))
      ),
      g
    )
    assert(FetcherGeneration.parts(plan, Nil, true, false).head.value == "unpaywall:open")
