package com.histmersion
package api.instance
import scala.annotation.tailrec
import com.histmersion.api.{Plural, ServiceState}
import com.histmersion.api.backup.Backup
import com.histmersion.api.library.{AcquireChecksMarkdown, UpstreamQuotaMarkdown}
import com.histmersion.api.maintenance.StorageMarkdown
import com.histmersion.api.plugins.PluginsMarkdown
import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.api.given

object AboutMarkdown:
  def age(seconds: Long): String =
    val s = math.max(0L, seconds)
    if s < 60 then (s"$s s")
    else if s < 3600 then (s"${s / 60} min")
    else if s < 86400 then (s"${s / 3600} h")
    else (s"${s / 86400} d")

  def render(a: About): String =
    val b = a.build
    val commit = b.gitCommit.fold("unknown")(c => if ((b.dirty) == BuildStamp.Dirty(true)) then s"$c (with uncommitted changes)" else c)
    val conv = a.health.converter
    val backup = a.lastBackup.fold("none")(bk =>
      s"${bk.file} · ${Backup.humanBytes(bk.bytes)}" + a.lastBackupAgeSeconds.fold("")(s => s" · ${age(s.value)} ago")
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
      s"- converter: ${ServiceState.of(conv.map(c => ServiceState.Wire(c.value))).label}" + conv
        .filterNot(_.value.trim.equalsIgnoreCase("ok"))
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
          s"- corpus: ${Plural.count(Plural.CountN(c.sources.value.toLong), Plural.CountSingular("source"))} · ${Plural.count(Plural.CountN(c.findings.value.toLong), Plural.CountSingular("finding"))} · " +
            s"${Plural.count(Plural.CountN(c.articles.value.toLong), Plural.CountSingular("article"))} · ${Plural.count(Plural.CountN(c.events.value.toLong), Plural.CountSingular("event"))}",
          s"- reference data: ${Plural.count(Plural.CountN(c.places.value.toLong), Plural.CountSingular("place"))} · ${Plural.count(Plural.CountN(c.periods.value.toLong), Plural.CountSingular("period"))} · ${Plural.count(Plural.CountN(c.presets.value.toLong), Plural.CountSingular("preset"))}"
        )
      ) ++ StorageMarkdown.render(a.storage) ++
      (if a.problems.isEmpty then Nil else "" :: "Problems:" :: a.problems.map(p => s"- $p"))
    (lines.mkString("\n"))
