package com.histmersion.api.basemap

import com.histmersion.api.backup.Backup
import com.histmersion.api.given
object BasemapMarkdown:

  type StatusResult = StatusResult.T
  object StatusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnDemandResult = OnDemandResult.T
  object OnDemandResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutsideResult = OutsideResult.T
  object OutsideResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutsideWhat = OutsideWhat.T
  object OutsideWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutsideKey = OutsideKey.T
  object OutsideKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainResult = PlainResult.T
  object PlainResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlainHtml = PlainHtml.T
  object PlainHtml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemovedResult = RemovedResult.T
  object RemovedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def status(s: Basemap.Status): com.histmersion.api.basemap.BasemapMarkdown.StatusResult =
    val head =
      if ((s.available) == _root_.com.histmersion.api.basemap.Basemap.Status.Available(true)) then "The local base map is downloaded."
      else s"The local base map is not downloaded — run `${s.fetchCommand}`."
    val rows = s.layers.map { l =>
      val what = Basemap.Layer.parse(l.layer).map(_.title).getOrElse(l.layer)
      if !((l.present) == _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Present(true)) then s"| ${l.layer} | $what | not downloaded | | | |"
      else
        val zoom = (l.minZoom, l.maxZoom) match
          case (Some(a), Some(b)) => s"$a–$b"
          case _                  => ""
        val state = l.error.fold(Backup.humanBytes(l.bytes))(e => s"unreadable: $e")
        s"| ${l.layer} | $what | $state | $zoom | ${l.bbox.getOrElse("")} | ${l.source.getOrElse(
            ""
          )}${l.fetchedAt.fold("")(t => s", $t")} |"
    }
    val table = ("| layer | what | size | zoom | region | source, fetched |" :: "|---|---|---|---|---|---|" :: rows)
      .mkString("\n")
    val kept = s.layers.find(l => l.layer == Basemap.Layer.Vector.wire && l.present).flatMap(_.kept)
    val terrain = s.layers.find(_.layer == Basemap.Layer.Terrain.wire)
    val focus =
      terrain.toList.flatMap(_.focus).map(f => s"- focus region `${f.name}`: zoom ${f.minZoom}–${f.maxZoom}, ${f.bbox}, ${Backup.humanBytes(f.bytes)}${f.fetchedAt.fold("")(t => s", $t")}")
    val notes = List(
      kept.map(k => s"The vector layer keeps $k."),
      terrain.flatMap(_.onDemand).map(onDemand),
      s.layers.find(_.layer == Basemap.Layer.Vector.wire).flatMap(_.onDemand).map(outside("The map", "vector", _)),
      s.layers
        .find(_.layer == Basemap.Layer.Bathymetry.wire)
        .flatMap(_.onDemand)
        .map(outside("The sea floor", "bathymetry", _)),
      Option.when(focus.nonEmpty)(("Relief kept deeper for:" :: focus).mkString("\n")),
      Option.when(((s.available) == _root_.com.histmersion.api.basemap.Basemap.Status.Available(true)) && s.styles.nonEmpty)(
        "Styles: " + s.styles
          .map { st =>
            if st.missing.isEmpty then st.name else s"${st.name} (lacks ${st.missing.mkString(", ")})"
          }
          .mkString(", ") + "."
      ),
      Some(
        s"Files: `${s.dir}` (${Backup.humanBytes(s.freeBytes)} free). The next fetch downloads `${s.bbox}` (basemap.bbox)."
      ),
      s.running.map(r => s"A fetch is running ($r) — `histmersion jobs`."),
      Option.when(s.hints.nonEmpty)(s.hints.mkString(" ")),
      Option.when(s.layers.exists(_.present))(
        "Credits: " + s.layers.filter(_.present).map(l => plain(l.attribution)).distinct.mkString(" · ")
      )
    ).flatten
    (head :: "" :: table :: "" :: notes).mkString("\n")

  def onDemand(o: Basemap.OnDemand): com.histmersion.api.basemap.BasemapMarkdown.OnDemandResult =
    if !((o.enabled) == _root_.com.histmersion.api.basemap.Basemap.OnDemand.Enabled(true)) then _root_.com.histmersion.api.basemap.BasemapMarkdown.OnDemandResult("Relief deeper than the extract: off (basemap.terrain.on-demand.enabled).")
    else
      s"Relief deeper than the extract: read when a page shows an area, to zoom ${o.maxZoom}; cached " +
        s"${o.tiles} tiles, ${Backup.humanBytes(o.bytes)} of ${Backup.humanBytes(o.maxBytes)} at `${o.dir}`" +
        o.offlineUntil.fold(".")(t => s" — the provider was unreachable; asked again after $t.")

  def outside(what: com.histmersion.api.basemap.BasemapMarkdown.OutsideWhat, key: com.histmersion.api.basemap.BasemapMarkdown.OutsideKey, o: Basemap.OnDemand): com.histmersion.api.basemap.BasemapMarkdown.OutsideResult =
    if !((o.enabled) == _root_.com.histmersion.api.basemap.Basemap.OnDemand.Enabled(true)) then _root_.com.histmersion.api.basemap.BasemapMarkdown.OutsideResult(s"$what outside the downloaded region: off (basemap.$key.on-demand.enabled).")
    else
      s"$what outside the downloaded region (and deeper than it): read when a page shows it, to zoom ${o.maxZoom}; cached " +
        s"${o.tiles} tiles, ${Backup.humanBytes(o.bytes)} of ${Backup.humanBytes(o.maxBytes)} at `${o.dir}`" +
        o.offlineUntil.fold(".")(t => s" — the provider was unreachable; asked again after $t.")

  def plain(html: com.histmersion.api.basemap.BasemapMarkdown.PlainHtml): com.histmersion.api.basemap.BasemapMarkdown.PlainResult = _root_.com.histmersion.api.basemap.BasemapMarkdown.PlainResult((html).value.replaceAll("<[^>]+>", ""))

  def removed(r: Basemap.Removed): com.histmersion.api.basemap.BasemapMarkdown.RemovedResult =
    if r.removed.isEmpty then _root_.com.histmersion.api.basemap.BasemapMarkdown.RemovedResult("Nothing to remove: no base-map file is downloaded.")
    else s"Removed ${Backup.humanBytes(r.bytes)}:\n" + r.removed.map(f => s"- `$f`").mkString("\n")
