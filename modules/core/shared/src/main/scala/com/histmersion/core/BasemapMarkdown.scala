package com.histmersion.core

/** How the terrain base map's answers read as Markdown (`histmersion basemap status|rm`). Pure. */
object BasemapMarkdown:

  def status(s: BasemapStatus): String =
    val head =
      if s.available then "The terrain base map is downloaded."
      else s"The terrain base map is not downloaded — run `${s.fetchCommand}`."
    val rows = s.layers.map { l =>
      val what = BasemapLayer.parse(l.layer).map(_.title).getOrElse(l.layer)
      if !l.present then s"| ${l.layer} | $what | not downloaded | | | |"
      else
        val zoom = (l.minZoom, l.maxZoom) match
          case (Some(a), Some(b)) => s"$a–$b"
          case _                  => ""
        val state = l.error.fold(Backup.humanBytes(l.bytes))(e => s"unreadable: $e")
        s"| ${l.layer} | $what | $state | $zoom | ${l.bbox.getOrElse("")} | ${l.source.getOrElse("")}${l.fetchedAt.fold("")(t => s", $t")} |"
    }
    val table = ("| layer | what | size | zoom | region | source, fetched |" :: "|---|---|---|---|---|---|" :: rows).mkString("\n")
    val notes = List(
      Some(s"Files: `${s.dir}` (${Backup.humanBytes(s.freeBytes)} free). The next fetch downloads `${s.bbox}` (basemap.bbox)."),
      s.running.map(r => s"A fetch is running ($r) — `histmersion jobs`."),
      Option.when(s.layers.exists(_.present))("Credits: " + s.layers.filter(_.present).map(_.attribution).distinct.mkString(" · "))
    ).flatten
    (head :: "" :: table :: "" :: notes).mkString("\n")

  def removed(r: BasemapRemoved): String =
    if r.removed.isEmpty then "Nothing to remove: no base-map file is downloaded."
    else s"Removed ${Backup.humanBytes(r.bytes)}:\n" + r.removed.map(f => s"- `$f`").mkString("\n")
