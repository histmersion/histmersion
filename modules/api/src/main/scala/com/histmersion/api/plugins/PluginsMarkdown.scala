package com.histmersion.api.plugins

import com.histmersion.api.Plural
import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.api.given
object PluginsMarkdown:

  def line(p: PluginStatus): String =
    val what = PluginState.parse(p.state) match
      case Some(PluginState.Registered) =>
        "registered" + (if p.provides.isEmpty then "" else p.provides.mkString(" (", ", ", ")"))
      case Some(PluginState.Failed)  => s"FAILED: ${p.error.getOrElse("no reason given")}"
      case Some(PluginState.Refused) => s"REFUSED: ${p.error.getOrElse("no reason given")}"
      case _                         => p.state
    s"${p.id} ${p.version} — $what"

  def summary(r: PluginsReport): List[String] =
    val enabled = r.plugins.count(_.enabled)
    val head = s"- plugins: ${Plural.count(r.plugins.size, "plugin")} in this binary · $enabled enabled" +
      (if r.ok then "" else " · PROBLEMS")
    head :: s"  - plugin set: ${PluginSet.describe(r.set)}" :: (r.plugins.map(p => s"  - ${line(p)}") ++
      r.plugins.flatMap(p => p.warnings.map(w => s"  - warning: $w")) ++
      r.warnings.map(w => s"  - warning: $w"))

  def selfCheck(file: ConfigFileInfo, r: PluginsReport, checks: List[(Boolean, String)] = Nil): String =
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
