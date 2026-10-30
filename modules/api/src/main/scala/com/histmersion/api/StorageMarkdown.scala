package com.histmersion.api

object StorageMarkdown:

  def render(r: StorageReport): List[String] =
    if r.entries.isEmpty && !r.measuring && r.problems.isEmpty then Nil
    else
      val when = (r.ageSeconds.map(s => s"measured ${AboutMarkdown.age(s)} ago") ++
        r.tookMillis.map(ms => s"took $ms ms") ++ Option.when(r.measuring)("measuring…")).mkString(", ")
      val head = s"## Storage" + (if r.entries.nonEmpty then s" — ${Backup.humanBytes(Storage.total(r.entries))} in all"
                                  else "") +
        (if when.nonEmpty then s" ($when)" else "")
      val entries = Storage.shown(r.entries).map((e, depth) => entry(r, e, depth))
      val vols = r.volumes.map(v =>
        s"- free space: ${v.id}: ${Backup.humanBytes(v.freeBytes)} free of ${Backup.humanBytes(v.totalBytes)}" +
          (if v.freeBytes < r.lowFreeBytes then " — LOW" else "")
      )
      val hints = r.hints.map(h => s"- $h")
      val problems = r.problems.map(p => s"- problem: $p")
      List("", head, "") ++ (if r.entries.isEmpty && r.measuring then List("- measuring…") else entries) ++
        vols ++ (if hints.isEmpty then Nil else "" :: hints) ++ problems

  private def entry(r: StorageReport, e: StorageEntry, depth: Int): String =
    val indent = "  " * depth
    val host = if e.host == "server" then "" else " [on this machine]"
    s"$indent- ${e.label}$host: ${(Storage.size(e) :: Storage.details(r, e) ++ e.path.toList).mkString(" · ")}"
