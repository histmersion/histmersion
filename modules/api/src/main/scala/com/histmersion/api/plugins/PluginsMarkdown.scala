package com.histmersion
package api.plugins

import com.histmersion.api.Plural
import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.api.given
import com.histmersion.lint.{tolerated, LintRule}
object PluginsMarkdown:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def line(p: PluginStatus): String =
    val what = PluginState.parse(p.state) match
      case Some(PluginState.Registered) =>
        "registered" + (if p.provides.isEmpty then "" else p.provides.mkString(" (", ", ", ")"))
      case Some(PluginState.Failed)  => s"FAILED: ${p.reasons}"
      case Some(PluginState.Refused) => s"REFUSED: ${p.reasons}"
      case _                         => p.state.value
    (s"${p.id} ${p.version} — $what")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def summary(r: PluginsReport): List[String] =
    val enabled = r.plugins.count((refactorCallback2 => ((refactorCallback2.enabled) == PluginStatus.Enabled.Enabled)))
    val head =
      s"- plugins: ${Plural.count(Plural.CountN((r.plugins.size).toLong), Plural.CountSingular("plugin"))} in this binary · $enabled enabled" +
        (if (r.ok) == (true) then "" else " · PROBLEMS")
    (head :: s"  - plugin set: ${PluginSet.describe(r.set)}" :: (r.plugins.map(p => s"  - ${line(p)}") ++
      r.plugins.flatMap(p => p.warnings.map(w => s"  - warning: $w")) ++
      r.warnings.map(w => s"  - warning: $w"))).map(refactorValue => (refactorValue))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def selfCheck(file: ConfigFileInfo, r: PluginsReport, checks: List[(Boolean, String)] = Nil): String =
    val plugins = r.plugins.flatMap { p =>
      s"- ${line(p)}" :: (p.settings.map(s => s"    ${s.key} = ${s.value}") ++ p.warnings.map(w => s"    warning: $w"))
    }
    val verdict = if ((r.ok) == (true)) && checks.forall((refactorCallback => (refactorCallback._1))) then
      "self-check passed"
    else "self-check FAILED"
    ((List(
      "# Histmersion self-check",
      "",
      s"- config file: ${ConfigFileInfo.describe(file)}",
      s"- plugin SPI: ${r.spiVersion}",
      s"- plugins: ${Plural.count(Plural.Count((r.plugins.size).toLong), Plural.Noun.Singular("plugin"))} · ${r.plugins
          .count((refactorCallback3 => ((refactorCallback3.enabled) == PluginStatus.Enabled.Enabled)))} enabled",
      s"- plugin set: ${PluginSet.describe(r.set)}",
      s"- plugin-set digest: ${r.set.fold(PluginSet.EmptyDigest)(_.digest)}"
    ) ++ checks.map((_, l) => s"- $l") ++ List("") ++ plugins ++ r.warnings.map(w => s"- warning: $w") ++ List(
      "",
      verdict
    )).mkString("\n"))
