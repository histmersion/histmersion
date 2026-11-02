package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import io.circe.syntax.*
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

class PluginsReportSuite extends munit.FunSuite:

  private val vault = PluginStatus(
    "keepassxc",
    "0.1.0",
    1,
    enabled = true,
    PluginState.Registered.wire,
    provides = List("secret-vault:keepassxc"),
    settings = List(PluginSetting("database", "/v.kdbx"), PluginSetting("password", "env:KP_PASSWORD"))
  )
  private val off = PluginStatus("bitwarden", "0.1.0", 1, enabled = false, PluginState.Disabled.wire)
  private val broken = PluginStatus(
    "portal",
    "2.0.0",
    1,
    enabled = true,
    PluginState.Failed.wire,
    warnings = List("histmersion.plugins.portal.token: a secret written into the config (value:***)"),
    error = Some("histmersion.plugins.portal.token: environment variable PORTAL_TOKEN is not set")
  )
  private val alien = PluginStatus(
    "old",
    "0.0.1",
    0,
    enabled = false,
    PluginState.Refused.wire,
    error = Some("old: built for SPI 0, this host speaks 1")
  )

  test("states have wire names and parse back"):
    assertEquals(PluginState.values.toList.map(_.wire), List("registered", "disabled", "failed", "refused"))
    assertEquals(PluginState.values.toList.flatMap(s => PluginState.parse(s.wire)), PluginState.values.toList)
    assertEquals(PluginState.parse(" Failed "), Some(PluginState.Failed))
    assertEquals(PluginState.parse("nope"), None)

  test("a report is ok unless a plugin failed or was refused; disabled ones are fine"):
    assert(PluginsReport(1, List(vault, off)).ok)
    assert(PluginsReport().ok)
    assert(!PluginsReport(1, List(vault, broken)).ok)
    assert(!PluginsReport(1, List(alien)).ok)

  test("one line per plugin says what it registered, or why not"):
    assertEquals(PluginsMarkdown.line(vault), "keepassxc 0.1.0 — registered (secret-vault:keepassxc)")
    assertEquals(PluginsMarkdown.line(off), "bitwarden 0.1.0 — disabled")
    assertEquals(
      PluginsMarkdown.line(broken),
      "portal 2.0.0 — FAILED: histmersion.plugins.portal.token: environment variable PORTAL_TOKEN is not set"
    )
    assertEquals(PluginsMarkdown.line(alien), "old 0.0.1 — REFUSED: old: built for SPI 0, this host speaks 1")

  test("histmersion about lists the plugins and their warnings"):
    val md = AboutMarkdown.render(
      About(
        "histmersion",
        BuildStamp("1"),
        Health("ok", "ok", None, None),
        JobCounts(),
        None,
        None,
        None,
        plugins = PluginsReport(1, List(vault, off, broken), List("histmersion.plugins.helo configures no plugin"))
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
      About("histmersion", BuildStamp("1"), Health("ok", "ok", None, None), JobCounts(), None, None, None)
    )
    assert(
      none.contains("- plugins: 0 plugins in this binary · 0 enabled\n  - plugin set: first-party plugins only"),
      none
    )
    // a relinked binary names its plugin set (docs/research-plugins.md §7.3)
    val set = PluginKit.pluginSet(List(PluginJar("hello-0.1.0.jar", "ab")), 1, "native", None, None)
    val linked = AboutMarkdown.render(
      About(
        "histmersion",
        BuildStamp("1"),
        Health("ok", "ok", None, None),
        JobCounts(),
        None,
        None,
        None,
        plugins = PluginsReport(1, set = Some(set))
      )
    )
    assert(linked.contains(s"  - plugin set: ${set.digest.take(12)}… · 1 jar (native): hello-0.1.0.jar"), linked)
    val check = PluginsMarkdown.selfCheck(ConfigFileInfo(), PluginsReport(1, set = Some(set)))
    assert(check.contains(s"- plugin-set digest: ${set.digest}"), check)
    assert(
      PluginsMarkdown
        .selfCheck(ConfigFileInfo(), PluginsReport(1))
        .contains(s"- plugin-set digest: ${PluginSet.EmptyDigest}")
    )

  test("the self-check report shows settings as references and ends with the verdict"):
    val ok = PluginsMarkdown.selfCheck(
      ConfigFileInfo(Some("/c/histmersion.conf"), loaded = true),
      PluginsReport(1, List(vault, off))
    )
    assert(ok.contains("- config file: /c/histmersion.conf (read)"), ok)
    assert(ok.contains("- plugin SPI: 1"), ok)
    assert(ok.contains("- plugins: 2 plugins · 1 enabled"), ok)
    assert(ok.contains("    password = env:KP_PASSWORD"), ok)
    assert(ok.endsWith("self-check passed"), ok)
    val bad = PluginsMarkdown.selfCheck(ConfigFileInfo(), PluginsReport(1, List(broken), List("w")))
    assert(bad.contains("- warning: w"), bad)
    assert(bad.endsWith("self-check FAILED"), bad)
    // the binary's own checks (the time zones) are listed, and one that fails fails the verdict
    val zones =
      PluginsMarkdown.selfCheck(ConfigFileInfo(), PluginsReport(1, List(vault)), List(true -> "time zones: ok"))
    assert(zones.contains("- plugin-set digest: " + PluginSet.EmptyDigest + "\n- time zones: ok\n\n- "), zones)
    assert(zones.endsWith("self-check passed"), zones)
    val noZones =
      PluginsMarkdown.selfCheck(ConfigFileInfo(), PluginsReport(1, List(vault)), List(false -> "time zones: BROKEN"))
    assert(noZones.contains("- time zones: BROKEN"), noZones)
    assert(noZones.endsWith("self-check FAILED"), noZones)

  test("the report crosses the wire; an About or UiConfig from a server without plugins still decodes"):
    val r = PluginsReport(1, List(vault, broken), List("w"))
    assertEquals(io.circe.parser.decode[PluginsReport](r.asJson.noSpaces), Right(r))
    val about = About("histmersion", BuildStamp("1"), Health("ok", "ok", None, None), JobCounts(), None, None, None)
    val older = about.asJson.mapObject(_.remove("plugins")).noSpaces
    assertEquals(io.circe.parser.decode[About](older).map(_.plugins), Right(PluginsReport()))
