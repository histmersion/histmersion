package com.histmersion
package api.basemap

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.backup.Backup
import com.histmersion.api.given
import com.histmersion.api.ByteCount
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
object BasemapMarkdown:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def status(s: Basemap.Status): String =
    val head =
      if (s.available) == Basemap.Status.Available(true) then "The local base map is downloaded."
      else s"The local base map is not downloaded — run `${s.fetchCommand}`."
    val rows = s.layers.map { l =>
      val what = Basemap.Layer.parse(l.layer).map(_.title).getOrElse(l.layer)
      if !((l.present) == Basemap.LayerInfo.Present(true)) then s"| ${l.layer} | $what | not downloaded | | | |"
      else
        val zoom = (l.minZoom, l.maxZoom) match
          case (Some(a), Some(b)) => s"$a–$b"
          case _                  => ""
        val state = l.error.fold(Backup.humanBytes(l.bytes))(e => s"unreadable: ${e.sentence}")
        s"| ${l.layer} | $what | $state | $zoom | ${l.bbox.getOrElse("")} | ${l.source.getOrElse(
            ""
          )}${l.fetchedAt.fold("")(t => s", $t")} |"
    }
    val table = ("| layer | what | size | zoom | region | source, fetched |" :: "|---|---|---|---|---|---|" :: rows)
      .mkString("\n")
    val kept = s.layers
      .find(l => l.layer == Basemap.Layer.Vector.wire && ((l.present) == Basemap.LayerInfo.Present(true)))
      .flatMap(_.kept)
    val terrain = s.layers.find(_.layer == Basemap.Layer.Terrain.wire)
    val focus =
      terrain.toList.flatMap(_.focus).map(f => s"- focus region `${f.name}`: zoom ${f.minZoom}–${f.maxZoom}, ${f.bbox}, ${Backup.humanBytes(f.bytes)}${f.fetchedAt.fold("")(t => s", $t")}")
    val notes = List(
      kept.map(k => s"The vector layer keeps $k."),
      terrain.flatMap(_.onDemand).map(onDemand),
      s.layers
        .find(_.layer == Basemap.Layer.Vector.wire)
        .flatMap(_.onDemand)
        .map(outside(("The map"), Basemap.Layer.Wire("vector"), _)),
      s.layers
        .find(_.layer == Basemap.Layer.Bathymetry.wire)
        .flatMap(_.onDemand)
        .map(outside(("The sea floor"), Basemap.Layer.Wire("bathymetry"), _)),
      Option.when(focus.nonEmpty)(("Relief kept deeper for:" :: focus).mkString("\n")),
      Option.when(((s.available) == Basemap.Status.Available(true)) && s.styles.nonEmpty)(
        "Styles: " + s.styles
          .map { st =>
            if st.missing.isEmpty then st.name else s"${st.name} (lacks ${st.missing.mkString(", ")})"
          }
          .mkString(", ") + "."
      ),
      Some(
        s"Files: `${s.dir}` (${Backup.humanBytes(ByteCount(s.freeBytes.value))} free). The next fetch downloads `${s.bbox}` (basemap.bbox)."
      ),
      s.running.map(r => s"A fetch is running ($r) — `histmersion jobs`."),
      Option.when(s.hints.nonEmpty)(s.hints.mkString(" ")),
      Option.when(
        s.layers.exists((refactorCallback => ((refactorCallback.present) == Basemap.LayerInfo.Present(true))))
      )(
        "Credits: " + s.layers
          .filter((refactorCallback => ((refactorCallback.present) == Basemap.LayerInfo.Present(true))))
          .map(l => plain(l.attribution.value))
          .distinct
          .mkString(" · ")
      )
    ).flatten
    (head :: "" :: table :: "" :: notes).mkString("\n")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def onDemand(o: Basemap.OnDemand): String =
    if !((o.enabled) == Basemap.OnDemand.Enabled(true)) then
      ("Relief deeper than the extract: off (basemap.terrain.on-demand.enabled).")
    else
      s"Relief deeper than the extract: read when a page shows an area, to zoom ${o.maxZoom}; cached " +
        s"${o.tiles} tiles, ${Backup.humanBytes(o.bytes)} of ${Backup.humanBytes(ByteCount(o.maxBytes.value))} at `${o.dir}`" +
        o.offlineUntil.fold(".")(t => s" — the provider was unreachable; asked again after $t.")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def outside(what: String, key: Basemap.Layer.Wire, o: Basemap.OnDemand): String =
    if !((o.enabled) == Basemap.OnDemand.Enabled(true)) then
      (s"$what outside the downloaded region: off (basemap.$key.on-demand.enabled).")
    else
      s"$what outside the downloaded region (and deeper than it): read when a page shows it, to zoom ${o.maxZoom}; cached " +
        s"${o.tiles} tiles, ${Backup.humanBytes(o.bytes)} of ${Backup.humanBytes(ByteCount(o.maxBytes.value))} at `${o.dir}`" +
        o.offlineUntil.fold(".")(t => s" — the provider was unreachable; asked again after $t.")

  def plain(html: String): String = ((html).replaceAll("<[^>]+>", ""))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def removed(r: Basemap.Removed): String =
    if r.removed.isEmpty then ("Nothing to remove: no base-map file is downloaded.")
    else s"Removed ${Backup.humanBytes(r.bytes)}:\n" + r.removed.map(f => s"- `$f`").mkString("\n")
