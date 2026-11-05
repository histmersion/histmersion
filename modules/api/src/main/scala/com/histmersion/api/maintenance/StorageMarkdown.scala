package com.histmersion.api.maintenance

import com.histmersion.api.backup.Backup
import com.histmersion.api.instance.AboutMarkdown
import com.histmersion.api.given
object StorageMarkdown:

  type RenderResult = RenderResult.T
  object RenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryResult = EntryResult.T
  object EntryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryDepth = EntryDepth.T
  object EntryDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def render(r: Storage.Report): List[com.histmersion.api.maintenance.StorageMarkdown.RenderResult] =
    if r.entries.isEmpty && !r.measuring && r.problems.isEmpty then Nil
    else
      val when = (r.ageSeconds.map(_.value.toLong).map(s => s"measured ${AboutMarkdown.age(s)} ago") ++
        r.tookMillis.map(_.value.toLong).map(ms => s"took $ms ms") ++ Option.when(r.measuring)("measuring…")).mkString(", ")
      val head = s"## Storage" + (if r.entries.nonEmpty then s" — ${Backup.humanBytes(Storage.total(r.entries).value.toLong)} in all"
                                  else "") +
        (if when.nonEmpty then s" ($when)" else "")
      val entries = Storage.shown(r.entries).map((e, depth) => entry(r, e, depth))
      val vols = r.volumes.map(v =>
        s"- free space: ${v.id}: ${Backup.humanBytes(v.freeBytes.value.toLong)} free of ${Backup.humanBytes(v.totalBytes.value.toLong)}" +
          (if v.freeBytes.value.toLong < r.lowFreeBytes.value.toLong then " — LOW" else "")
      )
      val hints = r.hints.map(h => s"- $h")
      val problems = r.problems.map(p => s"- problem: $p")
      List("", head, "") ++ (if r.entries.isEmpty && r.measuring then List("- measuring…") else entries) ++
        vols ++ (if hints.isEmpty then Nil else "" :: hints) ++ problems

  private def entry(r: Storage.Report, e: Storage.Entry, depth: com.histmersion.api.maintenance.StorageMarkdown.EntryDepth): com.histmersion.api.maintenance.StorageMarkdown.EntryResult =
    val indent = "  " * depth
    val host = if e.host == "server" then "" else " [on this machine]"
    s"$indent- ${e.label}$host: ${(Storage.size(e) :: Storage.details(r, e) ++ e.path.toList).mkString(" · ")}"
