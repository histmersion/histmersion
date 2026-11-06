package com.histmersion.api.instance
import scala.annotation.tailrec
import com.histmersion.api.{Plural, ServiceState}
import com.histmersion.api.backup.Backup
import com.histmersion.api.library.{AcquireChecksMarkdown, UpstreamQuotaMarkdown}
import com.histmersion.api.maintenance.StorageMarkdown
import com.histmersion.api.plugins.PluginsMarkdown
import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.api.given

object AboutMarkdown:

  type AgeResult = AgeResult.T
  object AgeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgeSeconds = AgeSeconds.T
  object AgeSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderResult = RenderResult.T
  object RenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def age(seconds: com.histmersion.api.instance.AboutMarkdown.AgeSeconds): com.histmersion.api.instance.AboutMarkdown.AgeResult =
    val s = math.max(0L, (seconds).value)
    if s < 60 then _root_.com.histmersion.api.instance.AboutMarkdown.AgeResult(s"$s s")
    else if s < 3600 then _root_.com.histmersion.api.instance.AboutMarkdown.AgeResult(s"${s / 60} min")
    else if s < 86400 then _root_.com.histmersion.api.instance.AboutMarkdown.AgeResult(s"${s / 3600} h")
    else _root_.com.histmersion.api.instance.AboutMarkdown.AgeResult(s"${s / 86400} d")

  def render(a: About): com.histmersion.api.instance.AboutMarkdown.RenderResult =
    val b = a.build
    val commit = b.gitCommit.fold("unknown")(c => if b.dirty then s"$c (with uncommitted changes)" else c)
    val conv = a.health.converter
    val backup = a.lastBackup.fold("none")(bk =>
      s"${bk.file} · ${Backup.humanBytes(bk.bytes)}" + a.lastBackupAgeSeconds.fold("")(s => s" · ${age(s)} ago")
    )
    val j = a.jobs
    val lines = List(
      s"# ${a.name} ${b.version}",
      "",
      s"- commit: $commit",
      s"- commit date: ${b.commitDate.getOrElse("unknown")}",
      s"- licence: ${b.licence.getOrElse("unknown")}",
      s"- source: ${b.sourceUrl.getOrElse("unknown")}",
      s"- schema version: ${a.health.schemaVersion.getOrElse("unknown")}",
      s"- status: ${a.health.status}",
      s"- database: ${a.health.db}",
      s"- converter: ${ServiceState.of(conv).label}" + conv
        .filterNot(_.trim.equalsIgnoreCase("ok"))
        .fold("")(t => s" ($t)"),
      s"- jobs: ${j.queued} queued · ${j.running} running · ${j.done} done · ${j.failed} failed",
      s"- last backup: $backup",
      s"- config file: ${ConfigFileInfo.describe(a.configFile)}",
      s"- agent lease: ${a.agentLease.fold("none (no server runs agents on this database)")(_.describe)}"
    ) ++ a.upstreams.map(q => s"- ${UpstreamQuotaMarkdown.line(q)}") ++
      a.acquireRecheck.map(r => s"- ${AcquireChecksMarkdown.line(r)}").toList ++ PluginsMarkdown.summary(
        a.plugins
      ) ++ a.counts.toList.flatMap(c =>
        List(
          s"- corpus: ${Plural.count(c.sources, _root_.com.histmersion.api.Plural.CountSingular("source"))} · ${Plural.count(c.findings, _root_.com.histmersion.api.Plural.CountSingular("finding"))} · " +
            s"${Plural.count(c.articles, "article")} · ${Plural.count(c.events, "event")}",
          s"- reference data: ${Plural.count(c.places, _root_.com.histmersion.api.Plural.CountSingular("place"))} · ${Plural.count(c.periods, _root_.com.histmersion.api.Plural.CountSingular("period"))} · ${Plural.count(c.presets, _root_.com.histmersion.api.Plural.CountSingular("preset"))}"
        )
      ) ++ StorageMarkdown.render(a.storage) ++
      (if a.problems.isEmpty then Nil else "" :: "Problems:" :: a.problems.map(p => s"- $p"))
    _root_.com.histmersion.api.instance.AboutMarkdown.RenderResult(lines.mkString("\n"))
