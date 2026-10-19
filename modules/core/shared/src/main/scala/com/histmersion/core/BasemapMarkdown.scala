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
    val kept = s.layers.find(l => l.layer == BasemapLayer.Vector.wire && l.present).flatMap(_.kept)
    val terrain = s.layers.find(_.layer == BasemapLayer.Terrain.wire)
    val focus = terrain.toList.flatMap(_.focus).map(f =>
      s"- focus region `${f.name}`: zoom ${f.minZoom}–${f.maxZoom}, ${f.bbox}, ${Backup.humanBytes(f.bytes)}${f.fetchedAt.fold("")(t => s", $t")}")
    val notes = List(
      kept.map(k => s"The vector layer keeps $k."),
      terrain.flatMap(_.onDemand).map(onDemand),
      Option.when(focus.nonEmpty)(("Relief kept deeper for:" :: focus).mkString("\n")),
      Some(s"Files: `${s.dir}` (${Backup.humanBytes(s.freeBytes)} free). The next fetch downloads `${s.bbox}` (basemap.bbox)."),
      s.running.map(r => s"A fetch is running ($r) — `histmersion jobs`."),
      Option.when(s.hints.nonEmpty)(s.hints.mkString(" ")),
      Option.when(s.layers.exists(_.present))("Credits: " + s.layers.filter(_.present).map(l => plain(l.attribution)).distinct.mkString(" · "))
    ).flatten
    (head :: "" :: table :: "" :: notes).mkString("\n")

  /** The on-demand relief in a line: to what zoom, its cache, whether it is reachable. Pure. */
  def onDemand(o: BasemapOnDemand): String =
    if !o.enabled then "Relief deeper than the extract: off (basemap.terrain.on-demand.enabled)."
    else s"Relief deeper than the extract: read when a page shows an area, to zoom ${o.maxZoom}; cached " +
      s"${o.tiles} tiles, ${Backup.humanBytes(o.bytes)} of ${Backup.humanBytes(o.maxBytes)} at `${o.dir}`" +
      o.offlineUntil.fold(".")(t => s" — the provider was unreachable; asked again after $t.")

  /** The named styles (`histmersion basemap styles`): the menu's entries, where they came from, and
    * what was refused or ignored. */
  def styles(t: TerrainStyles): String =
    val rows = t.styles.map { s =>
      val marks = List(Option.when(s.id == t.default)("default"), Option.when(s.tone == "light")("light"),
        Option.when(!s.labels || !s.hasLabels)("no names"), Option.when(!s.relief.enabled)("no relief")).flatten
      s"| `${s.id}` | ${s.name} | ${s.layers.size} | ${marks.mkString(", ")} | ${s.description} |"
    }
    val table = ("| id | name | layers | | |" :: "|---|---|---|---|---|" :: rows).mkString("\n")
    val file = t.file.fold("")(f => s" Style file: `$f`.")
    val notes = s"From: ${t.source}.$file Names in: ${t.language}." ::
      t.warnings.map(w => s"- $w")
    (s"${t.styles.size} terrain map ${if t.styles.size == 1 then "style" else "styles"}:" :: "" :: table :: "" :: notes)
      .mkString("\n")

  /** A style file's check (`histmersion basemap styles --check FILE`). */
  def styleCheck(c: TerrainStyleCheck): String =
    val head =
      if c.ok then s"The file is valid: the base-map menu would show ${c.styles.map(s => s"`$s`").mkString(", ")}" +
        c.default.fold("")(d => s" (default `$d`)") + "."
      else s"The file is NOT valid (${c.errors.size} ${if c.errors.size == 1 then "error" else "errors"}) — the server would keep the built-in styles:"
    (head :: c.errors.map(e => s"- $e") ::: c.warnings.map(w => s"- warning: $w")).mkString("\n")

  /** A credit line without its links (the page shows them; a terminal does not). */
  def plain(html: String): String = html.replaceAll("<[^>]+>", "")

  def removed(r: BasemapRemoved): String =
    if r.removed.isEmpty then "Nothing to remove: no base-map file is downloaded."
    else s"Removed ${Backup.humanBytes(r.bytes)}:\n" + r.removed.map(f => s"- `$f`").mkString("\n")
