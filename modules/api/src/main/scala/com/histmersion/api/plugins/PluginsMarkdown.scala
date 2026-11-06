package com.histmersion.api.plugins

import com.histmersion.api.Plural
import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.api.given
object PluginsMarkdown:

  type LineResult = LineResult.T
  object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SummaryResult = SummaryResult.T
  object SummaryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelfCheckResult = SelfCheckResult.T
  object SelfCheckResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelfCheckChecksPart1 = SelfCheckChecksPart1.T
  object SelfCheckChecksPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SelfCheckChecksPart2 = SelfCheckChecksPart2.T
  object SelfCheckChecksPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def line(p: PluginStatus): com.histmersion.api.plugins.PluginsMarkdown.LineResult =
    val what = PluginState.parse(p.state) match
      case Some(PluginState.Registered) =>
        "registered" + (if p.provides.isEmpty then "" else p.provides.mkString(" (", ", ", ")"))
      case Some(PluginState.Failed)  => s"FAILED: ${p.error.getOrElse("no reason given")}"
      case Some(PluginState.Refused) => s"REFUSED: ${p.error.getOrElse("no reason given")}"
      case _                         => p.state
    _root_.com.histmersion.api.plugins.PluginsMarkdown.LineResult(s"${p.id} ${p.version} — $what")

  def summary(r: PluginsReport): List[com.histmersion.api.plugins.PluginsMarkdown.SummaryResult] =
    val enabled = r.plugins.count(_.enabled)
    val head = s"- plugins: ${Plural.count(_root_.com.histmersion.api.Plural.CountN((r.plugins.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("plugin"))} in this binary · $enabled enabled" +
      (if r.ok then "" else " · PROBLEMS")
    (head :: s"  - plugin set: ${PluginSet.describe(r.set)}" :: (r.plugins.map(p => s"  - ${line(p)}") ++
      r.plugins.flatMap(p => p.warnings.map(w => s"  - warning: $w")) ++
      r.warnings.map(w => s"  - warning: $w"))).map(refactorValue => _root_.com.histmersion.api.plugins.PluginsMarkdown.SummaryResult(refactorValue))

  def selfCheck(file: ConfigFileInfo, r: PluginsReport, checks: List[(com.histmersion.api.plugins.PluginsMarkdown.SelfCheckChecksPart1, com.histmersion.api.plugins.PluginsMarkdown.SelfCheckChecksPart2)] = Nil): com.histmersion.api.plugins.PluginsMarkdown.SelfCheckResult =
    val plugins = r.plugins.flatMap { p =>
      s"- ${line(p)}" :: (p.settings.map(s => s"    ${s.key} = ${s.value}") ++ p.warnings.map(w => s"    warning: $w"))
    }
    val verdict = if r.ok && checks.forall(_._1) then "self-check passed" else "self-check FAILED"
    (List(
      "# Histmersion self-check",
      "",
      s"- config file: ${ConfigFileInfo.describe(file)}",
      s"- plugin SPI: ${r.spiVersion}",
      s"- plugins: ${Plural.count(r.plugins.size, "plugin")} · ${r.plugins.count(_.enabled)} enabled",
      s"- plugin set: ${PluginSet.describe(r.set)}",
      s"- plugin-set digest: ${r.set.fold(PluginSet.EmptyDigest)(_.digest)}"
    ) ++ checks.map((_, l) => s"- $l") ++ List("") ++ plugins ++ r.warnings.map(w => s"- warning: $w") ++ List(
      "",
      verdict
    )).mkString("\n")
