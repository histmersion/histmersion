package com.histmersion
package api
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.Problem

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.util.plugins.PluginKit
import com.histmersion.api.instance.{About, AboutMarkdown, BuildStamp, Health}
import com.histmersion.api.jobs.JobCounts
import com.histmersion.api.plugins.{
  PluginJar,
  PluginSet,
  PluginSetting,
  PluginsMarkdown,
  PluginsReport,
  PluginState,
  PluginStatus
}
import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.api.plugins.PluginDescription

class PluginsReportSuite extends munit.FunSuite:

  private val vault = PluginStatus(
    PluginStatus.Id("keepassxc"),
    PluginStatus.Version("0.1.0"),
    PluginStatus.SpiVersion(1),
    enabled = PluginStatus.Enabled(true),
    PluginState.Wire(PluginState.Registered.wire.value),
    provides = List(PluginDescription.Provides("secret-vault:keepassxc")),
    settings = List(
      PluginSetting(PluginSetting.Key("database"), PluginSetting.Value("/v.kdbx")),
      PluginSetting(PluginSetting.Key("password"), PluginSetting.Value("env:KP_PASSWORD"))
    )
  )
  private val off = PluginStatus(
    PluginStatus.Id("bitwarden"),
    PluginStatus.Version("0.1.0"),
    PluginStatus.SpiVersion(1),
    enabled = PluginStatus.Enabled(false),
    PluginState.Wire(PluginState.Disabled.wire.value)
  )
  private val broken = PluginStatus(
    PluginStatus.Id("portal"),
    PluginStatus.Version("2.0.0"),
    PluginStatus.SpiVersion(1),
    enabled = PluginStatus.Enabled(true),
    PluginState.Wire(PluginState.Failed.wire.value),
    warnings =
      List(PluginStatus.Warnings("histmersion.plugins.portal.token: a secret written into the config (value:***)")),
    errors = List(
      Problem.Config.Setting(
        Problem.SettingKey("histmersion.plugins.portal"),
        Problem.Config.Fault.Unset(Problem.SettingKey("PORTAL_TOKEN"))
      ),
      Problem.Plugin.Vault(Problem.Plugin.Id("keepassxc"), Problem.Plugin.Retrieval.NoVault)
    )
  )
  private val alien = PluginStatus(
    PluginStatus.Id("old"),
    PluginStatus.Version("0.0.1"),
    PluginStatus.SpiVersion(0),
    enabled = PluginStatus.Enabled(false),
    PluginState.Wire(PluginState.Refused.wire.value),
    errors = List(
      Problem.Plugin
        .Load(Problem.Plugin.Id("old"), Problem.Plugin.Fault.SpiMismatch(Problem.Raw("0"), Problem.Raw("1")))
    )
  )

  test("states have wire names and parse back"):
    assertEquals(PluginState.values.toList.map(_.wire), List("registered", "disabled", "failed", "refused"))
    assertEquals(PluginState.values.toList.flatMap(s => PluginState.parse(s.wire)), PluginState.values.toList)
    assertEquals(PluginState.parse(PluginState.Wire(" Failed ")), Some(PluginState.Failed))
    assertEquals(PluginState.parse(PluginState.Wire("nope")), None)

  test("a report is ok unless a plugin failed or was refused; disabled ones are fine"):
    assert(PluginsReport(PluginsReport.SpiVersion(1), List(vault, off)).ok)
    assert(PluginsReport().ok)
    assert(!PluginsReport(PluginsReport.SpiVersion(1), List(vault, broken)).ok)
    assert(!PluginsReport(PluginsReport.SpiVersion(1), List(alien)).ok)

  test("one line per plugin says what it registered, or why not"):
    assertEquals(PluginsMarkdown.line(vault), "keepassxc 0.1.0 — registered (secret-vault:keepassxc)")
    assertEquals(PluginsMarkdown.line(off), "bitwarden 0.1.0 — disabled")
    assertEquals(
      PluginsMarkdown.line(broken),
      "portal 2.0.0 — FAILED: histmersion.plugins.portal reads PORTAL_TOKEN, which is not set; secret vault " +
        "keepassxc failed: no vault of that name is registered (enable the plugin that provides it)"
    )
    assertEquals(
      PluginsMarkdown.line(alien),
      "old 0.0.1 — REFUSED: plugin old did not load: it was built against plugin SPI 0; this Histmersion speaks SPI 1 " +
        "— rebuild the plugin against SPI 1"
    )

  test("histmersion about lists the plugins and their warnings"):
    val md = AboutMarkdown.render(
      About(
        About.Name("histmersion"),
        BuildStamp(BuildStamp.Version("1")),
        Health(Health.Status("ok"), Health.Db("ok"), None, None),
        JobCounts(),
        None,
        None,
        None,
        plugins = PluginsReport(
          PluginsReport.SpiVersion(1),
          List(vault, off, broken),
          List(PluginsReport.Warnings("histmersion.plugins.helo configures no plugin"))
        )
      )
    )
    assert(md.contains("- plugins: 3 plugins in this binary · 2 enabled · PROBLEMS"), md)
    assert(md.contains("  - keepassxc 0.1.0 — registered (secret-vault:keepassxc)"), md)
    assert(
      md.contains("  - warning: histmersion.plugins.portal.token: a secret written into the config (value:***)"),
      md
    )
    assert(md.contains("  - warning: histmersion.plugins.helo configures no plugin"), md)
    val none = AboutMarkdown.render(
      About(
        About.Name("histmersion"),
        BuildStamp(BuildStamp.Version("1")),
        Health(Health.Status("ok"), Health.Db("ok"), None, None),
        JobCounts(),
        None,
        None,
        None
      )
    )
    assert(
      none.contains("- plugins: 0 plugins in this binary · 0 enabled\n  - plugin set: first-party plugins only"),
      none
    )
    // a relinked binary names its plugin set (docs/research-plugins.md §7.3)
    val set = PluginKit.pluginSet(
      List(PluginJar(PluginJar.File("hello-0.1.0.jar"), Sha256("ab"))),
      PluginSet.SpiVersion(1),
      PluginSet.Mode("native"),
      None,
      None
    )
    val linked = AboutMarkdown.render(
      About(
        About.Name("histmersion"),
        BuildStamp(BuildStamp.Version("1")),
        Health(Health.Status("ok"), Health.Db("ok"), None, None),
        JobCounts(),
        None,
        None,
        None,
        plugins = PluginsReport(PluginsReport.SpiVersion(1), set = Some(set))
      )
    )
    assert(linked.contains(s"  - plugin set: ${set.digest.value.take(12)}… · 1 jar (native): hello-0.1.0.jar"), linked)
    val check = PluginsMarkdown.selfCheck(ConfigFileInfo(), PluginsReport(PluginsReport.SpiVersion(1), set = Some(set)))
    assert(check.contains(s"- plugin-set digest: ${set.digest}"), check)
    assert(
      PluginsMarkdown
        .selfCheck(ConfigFileInfo(), PluginsReport(PluginsReport.SpiVersion(1)))
        .contains(s"- plugin-set digest: ${PluginSet.EmptyDigest}")
    )

  test("the self-check report shows settings as references and ends with the verdict"):
    val ok = PluginsMarkdown.selfCheck(
      ConfigFileInfo(Some(ConfigFileInfo.Path("/c/histmersion.conf")), loaded = ConfigFileInfo.Loaded(true)),
      PluginsReport(PluginsReport.SpiVersion(1), List(vault, off))
    )
    assert(ok.contains("- config file: /c/histmersion.conf (read)"), ok)
    assert(ok.contains("- plugin SPI: 1"), ok)
    assert(ok.contains("- plugins: 2 plugins · 1 enabled"), ok)
    assert(ok.contains("    password = env:KP_PASSWORD"), ok)
    assert(ok.endsWith("self-check passed"), ok)
    val bad = PluginsMarkdown.selfCheck(
      ConfigFileInfo(),
      PluginsReport(PluginsReport.SpiVersion(1), List(broken), List(PluginsReport.Warnings("w")))
    )
    assert(bad.contains("- warning: w"), bad)
    assert(bad.endsWith("self-check FAILED"), bad)
    // the binary's own checks (the time zones) are listed, and one that fails fails the verdict
    val zones =
      PluginsMarkdown.selfCheck(
        ConfigFileInfo(),
        PluginsReport(PluginsReport.SpiVersion(1), List(vault)),
        List(true -> "time zones: ok")
      )
    assert(zones.contains("- plugin-set digest: " + PluginSet.EmptyDigest + "\n- time zones: ok\n\n- "), zones)
    assert(zones.endsWith("self-check passed"), zones)
    val noZones =
      PluginsMarkdown.selfCheck(
        ConfigFileInfo(),
        PluginsReport(PluginsReport.SpiVersion(1), List(vault)),
        List(false -> "time zones: BROKEN")
      )
    assert(noZones.contains("- time zones: BROKEN"), noZones)
    assert(noZones.endsWith("self-check FAILED"), noZones)

  test("the report crosses the wire; an About or UiConfig from a server without plugins still decodes"):
    val r = PluginsReport(PluginsReport.SpiVersion(1), List(vault, broken), List(PluginsReport.Warnings("w")))
    // a problem inside a record crosses the wire as its sentence until the api's records carry the typed problem
    val back = RawJson.decode[PluginsReport](r.asJson.noSpaces)
    assertEquals(back.map(_.plugins.map(_.reasons)), Right(r.plugins.map(_.reasons)))
    assertEquals(back.map(_.plugins.map(_.errors.size)), Right(List(0, 2)))
    assertEquals(back.map(_.warnings), Right(r.warnings))
    val about = About(
      About.Name("histmersion"),
      BuildStamp(BuildStamp.Version("1")),
      Health(Health.Status("ok"), Health.Db("ok"), None, None),
      JobCounts(),
      None,
      None,
      None
    )
    val older = about.asJson.mapObject(_.remove("plugins")).noSpaces
    assertEquals(RawJson.decode[About](older).map(_.plugins), Right(PluginsReport()))
