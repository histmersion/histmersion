package com.histmersion
package web

import com.histmersion.api.YearFmt
import com.histmersion.api.Html
import com.histmersion.api.Html.html as markup
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js
import com.histmersion.api.catalogue.{EventGeo, EventPrecision, EventScale, EventTaxonomy}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.UiLabels
import com.histmersion.lint.{tolerated, LintRule}

/** The pure half of the events in playback (#1440): when an event shows and how strongly. */
object EventPlayback:
  type Alpha = Alpha.T
  object Alpha extends Newtype[Double, Pure]
  type AnchorKey = AnchorKey.T
  object AnchorKey extends Newtype[String, Pure]
  type FadeYears = FadeYears.T
  object FadeYears extends Newtype[Double, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
  type Rank = Rank.T
  object Rank extends Newtype[Double, Pure]
  type Seconds = Seconds.T
  object Seconds extends Newtype[Double, Pure]
  type Slot = Slot.T
  object Slot extends Newtype[Int, Pure]
  type YearsPerSecond = YearsPerSecond.T
  object YearsPerSecond extends Newtype[Double, Pure]

  /** Pure: how visible an event is with the playhead at `playhead` — 0 before its span, 1 inside it, then falling
    * linearly to 0 over `fadeYears` after it ends (a battle of one year stays on screen for the fade, not for one
    * frame).
    */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def alpha(span: (Year, Year), playhead: Year, fadeYears: FadeYears): Alpha =
    val (start, end) = span
    Alpha(
      if playhead < start then 0.0
      else if playhead <= end then 1.0
      else if fadeYears.value <= 0 then 0.0
      else (1.0 - (playhead.value - end.value).toDouble / fadeYears.value).max(0.0)
    )

  /** Pure: the years a fade of `seconds` of playback takes at `yearsPerSecond`. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fadeYears(seconds: Seconds, yearsPerSecond: YearsPerSecond): FadeYears = FadeYears(
    (seconds.value * yearsPerSecond.value).max(0.0)
  )

  /** Pure: `alpha` rounded to twentieths — what the page is told, so a slow fade does not rewrite the same element on
    * every frame.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def step(alpha: Alpha): Alpha = Alpha(math.round(alpha.value * 20).toDouble / 20)

  /** Pure: the events playback draws, with the years each covers: the dated ones precise enough to stand at a year
    * ([[EventPrecision.mapped]] — the feed leaves the imprecise out already).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def playable(events: List[EventGeo]): Vector[(EventGeo, (Year, Year))] =
    events.toVector
      .filter(e => EventPrecision.mapped(EventSummary.Precision(e.precision.value)))
      .flatMap(e => e.timeSpan.outer.map(span => (e, span)))

  /** Pure: the line under an event's name — its kind and its years. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def note(e: EventGeo): Note =
    Note(s"${Fmt.display(UiLabels.Text(e.kind.value))} · ${YearFmt.span(e.timeSpan)}")

  /** Pure: the key events share when they are drawn at the same point (a site with several destructions): their labels
    * are stacked there rather than drawn over each other.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def anchorKey(e: EventGeo): AnchorKey = AnchorKey(f"${e.lat.value}%.4f|${e.lon.value}%.4f")

  /** Pure: the stacking slot of each SHOWN event at one anchor — `ids` in their order there (by start), numbered 0, 1,
    * 2… among those shown, so a label moves up when the one above it goes.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def slots[A](ids: Seq[A], shown: A => Boolean): Map[A, Slot] =
    ids.filter(shown).zipWithIndex.map((a, i) => a -> Slot(i)).toMap

  /** Pure: an event name's rank in the map's collision pass ([[MapLabels]], [[LabelPlacement.cull]]): above every area
    * name (whose rank is its zoom-0 area, at most the 256² px world) and point name — an event is on screen for a
    * moment, the static names return when it goes; a local event above a regional one (the battle stays readable inside
    * its war); among each, the later start first (the event just arrived wins over the one fading out).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rank(e: EventGeo, start: Year): Rank =
    Rank((if e.scale.value == EventScale.Regional then RegionalRank else LocalRank) + start.value.toDouble)

  private val RegionalRank = 1e9
  private val LocalRank = 2e9

  /** Pure: the label marker's markup. It is the point-only region label of [[PlaceGlyph]] — the same `.hd-area-point`
    * halo name and note — inside an `.hd-event` wrapper that says local (with the dot at the site) or regional; the
    * wrapper carries the visibility as `--a`. The name starts hidden (`is-culled`): the map's label pass shows it once
    * it collides with no higher-ranked name.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def html(e: EventGeo, alpha: Alpha): Html =
    val local = e.scale.value != EventScale.Regional
    val title =
      s"${e.label} — ${note(e)}${e.dating.fold("")(d => s" · $d")}${e.placeLabel.fold("")(p => s" · $p")} (${e.historicity})"
    // the dot carries the kind's icon (#1449): what happened reads before the name does
    val dot = if local then
      markup"""<span class="hd-event-dot hd-badge">${Glyphs.badge(
          Glyphs.BadgeName(EventTaxonomy.icon(EventSummary.Kind(e.kind.value)).value)
        )}</span>"""
    else Html.empty
    val approx = if e.precision.value == EventPrecision.Approximate then " hd-event--approximate" else ""
    val scale = if local then "hd-event--local" else "hd-event--regional"
    markup"""<span class="hd-event $scale$approx" style="--a:$alpha" """ ++
      markup"""title="$title">$dot<span class="hd-area-point is-culled">""" ++
      markup"""<span class="hd-area-point-name">${e.label}</span>""" ++
      markup"""<span class="hd-area-point-note">${note(e)}</span></span></span>"""
