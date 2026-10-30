package com.histmersion.web

import com.histmersion.api.*
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** The pure half of the events in playback (#1440): when an event shows and how strongly. */
object EventPlayback:

  /** Pure: how visible an event is with the playhead at `playhead` — 0 before its span, 1 inside it, then falling
    * linearly to 0 over `fadeYears` after it ends (a battle of one year stays on screen for the fade, not for one
    * frame).
    */
  def alpha(span: (Long, Long), playhead: Long, fadeYears: Double): Double =
    val (start, end) = span
    if playhead < start then 0.0
    else if playhead <= end then 1.0
    else if fadeYears <= 0 then 0.0
    else (1.0 - (playhead - end).toDouble / fadeYears).max(0.0)

  /** Pure: the years a fade of `seconds` of playback takes at `yearsPerSecond`. */
  def fadeYears(seconds: Double, yearsPerSecond: Double): Double = (seconds * yearsPerSecond).max(0.0)

  /** Pure: `alpha` rounded to twentieths — what the page is told, so a slow fade does not rewrite the same element on
    * every frame.
    */
  def step(alpha: Double): Double = math.round(alpha * 20).toDouble / 20

  /** Pure: the events playback draws, with the years each covers: the dated ones precise enough to stand at a year
    * ([[EventPrecision.mapped]] — the feed leaves the imprecise out already).
    */
  def playable(events: List[EventGeo]): Vector[(EventGeo, (Long, Long))] =
    events.toVector
      .filter(e => EventPrecision.mapped(e.precision))
      .flatMap(e => EventGeo.outer(e.timeSpan).map(span => (e, span)))

  /** Pure: the line under an event's name — its kind and its years. */
  def note(e: EventGeo): String =
    s"${Fmt.display(e.kind)} · ${YearFmt.span(e.timeSpan)}"

  /** Pure: the key events share when they are drawn at the same point (a site with several destructions): their labels
    * are stacked there rather than drawn over each other.
    */
  def anchorKey(e: EventGeo): String = f"${e.lat}%.4f|${e.lon}%.4f"

  /** Pure: the stacking slot of each SHOWN event at one anchor — `ids` in their order there (by start), numbered 0, 1,
    * 2… among those shown, so a label moves up when the one above it goes.
    */
  def slots(ids: Seq[String], shown: String => Boolean): Map[String, Int] =
    ids.filter(shown).zipWithIndex.toMap

  /** Pure: an event name's rank in the map's collision pass ([[MapLabels]], [[LabelPlacement.cull]]): above every area
    * name (whose rank is its zoom-0 area, at most the 256² px world) and point name — an event is on screen for a
    * moment, the static names return when it goes; a local event above a regional one (the battle stays readable inside
    * its war); among each, the later start first (the event just arrived wins over the one fading out).
    */
  def rank(e: EventGeo, start: Long): Double =
    (if e.scale == EventScale.Regional then RegionalRank else LocalRank) + start.toDouble

  private val RegionalRank = 1e9
  private val LocalRank = 2e9

  /** Pure: the label marker's markup. It is the point-only region label of [[PlaceGlyph]] — the same `.hd-area-point`
    * halo name and note — inside an `.hd-event` wrapper that says local (with the dot at the site) or regional; the
    * wrapper carries the visibility as `--a`. The name starts hidden (`is-culled`): the map's label pass shows it once
    * it collides with no higher-ranked name.
    */
  def html(e: EventGeo, alpha: Double): String =
    val local = e.scale != EventScale.Regional
    val title =
      s"${e.label} — ${note(e)}${e.dating.fold("")(d => s" · $d")}${e.placeLabel.fold("")(p => s" · $p")} (${e.historicity})"
    // the dot carries the kind's icon (#1449): what happened reads before the name does
    val dot = if local then
      s"""<span class="hd-event-dot hd-badge">${Glyphs.badge(EventTaxonomy.icon(e.kind))}</span>"""
    else ""
    val approx = if e.precision == EventPrecision.Approximate then " hd-event--approximate" else ""
    s"""<span class="hd-event ${
        if local then "hd-event--local" else "hd-event--regional"
      }$approx" style="--a:$alpha" """ +
      s"""title="${Fmt.esc(title)}">$dot<span class="hd-area-point is-culled">""" +
      s"""<span class="hd-area-point-name">${Fmt.esc(e.label)}</span>""" +
      s"""<span class="hd-area-point-note">${Fmt.esc(note(e))}</span></span></span>"""
