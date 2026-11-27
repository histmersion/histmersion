package com.histmersion
package api.maintenance

import com.histmersion.api.backup.Backup
import com.histmersion.api.instance.AboutMarkdown
import com.histmersion.api.given
import com.histmersion.api.ByteCount
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
object StorageMarkdown:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def render(r: Storage.Report): List[String] =
    if r.entries.isEmpty && !((r.measuring) == Storage.Report.Measuring(true)) && r.problems.isEmpty then Nil
    else
      val when = (r.ageSeconds.map(_.value.toLong).map(s => s"measured ${AboutMarkdown.age(s)} ago") ++
        r.tookMillis.map(_.value.toLong).map(ms => s"took $ms ms") ++ Option.when(
          ((r.measuring) == Storage.Report.Measuring(true))
        )("measuring…")).mkString(", ")
      val head = s"## Storage" + (if r.entries.nonEmpty then
                                    s" — ${Backup.humanBytes(ByteCount(Storage.total(r.entries).value.toLong))} in all"
                                  else "") +
        (if when.nonEmpty then s" ($when)" else "")
      val entries = Storage.shown(r.entries).map((e, depth) => entry(r, e, depth))
      val vols = r.volumes.map(v =>
        s"- free space: ${v.id}: ${Backup.humanBytes(ByteCount(v.freeBytes.value.toLong))} free of ${Backup.humanBytes(ByteCount(v.totalBytes.value.toLong))}" +
          (if v.freeBytes.value.toLong < r.lowFreeBytes.value.toLong then " — LOW" else "")
      )
      val hints = r.hints.map(h => s"- $h")
      val problems = r.problems.map(p => s"- problem: $p")
      List("", head, "") ++ (if r.entries.isEmpty && ((r.measuring) == Storage.Report.Measuring(true)) then
                               List("- measuring…")
                             else entries) ++
        vols ++ (if hints.isEmpty then Nil else "" :: hints) ++ problems

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def entry(r: Storage.Report, e: Storage.Entry, depth: Storage.Depth): String =
    val indent = "  " * (depth).value
    val host = if e.host == Storage.Entry.Host("server") then "" else " [on this machine]"
    (s"$indent- ${e.label}$host: ${(Storage.size(e) :: Storage.details(r, e) ++ e.path.toList).mkString(" · ")}")
