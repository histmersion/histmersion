package com.histmersion.web

import com.histmersion.api.ConfidenceStyle
import com.histmersion.values.Confidence

/** How sure the map's dots for some findings are (#1946): a STATED confidence, or [[Certainty.Unstated]] — a finding
  * that states none. An unstated one is drawn in its own neutral look (its period colour turned grey, a dashed edge, a
  * pip of its own in the legend), never as high: the map read a missing confidence as high, the most certain look for
  * the claim it knew least about. Pure.
  */
enum Certainty:
  case Stated(level: Confidence)
  case Unstated

object Certainty:

  /** What a dot that is not a finding (a site, an event, a picture) says about certainty: nothing — the full colour,
    * which never outweighs a finding's own.
    */
  val NoSay: Certainty = Stated(Confidence.High)

  /** A finding's certainty: its confidence, or unstated when it has none. */
  def of(c: Option[Confidence]): Certainty = c.fold(Unstated)(Stated(_))

  /** Weakest first: `contested < low < medium < unstated < high`. An unstated finding never lets a group look high,
    * while a stated low or contested in the same group says more than "not stated" does.
    */
  def rank(c: Certainty): Int = c match
    case Stated(level) => 2 * ConfidenceStyle.rank(level)
    case Unstated      => 2 * ConfidenceStyle.rank(Confidence.High) - 1

  /** The certainty a group of dots is drawn with: its weakest; [[NoSay]] for none. */
  def worst(items: Iterable[Certainty]): Certainty =
    items.foldLeft(NoSay)((w, c) => if rank(c) < rank(w) then c else w)

  /** The certainty of a group of findings ([[worst]] of [[of]]). */
  def ofAll(items: Iterable[Option[Confidence]]): Certainty = worst(items.map(of))

  /** `rgb(r,g,b)` as the dot is drawn: desaturated by a stated level ([[ConfidenceStyle]], the same palette as the page
    * badges), turned to its luminance grey when unstated — the neutral look keeps the period ramp's lightness, so an
    * unstated dot still reads older or newer. Anything that is not an `rgb(…)` string comes back unchanged.
    */
  def desaturate(rgb: String, c: Certainty): String = c match
    case Stated(level) => ConfidenceStyle.desaturate(rgb, level)
    case Unstated      =>
      "\\d+".r.findAllIn(rgb).take(3).map(_.toInt).toList match
        case r :: g :: b :: Nil =>
          val grey = (0.299 * r + 0.587 * g + 0.114 * b).round.toInt
          s"rgb($grey,$grey,$grey)"
        case _ => rgb

  /** The marker class of an unstated dot (its dashed edge, `style.css`); none for a stated one. */
  def markerClass(c: Certainty): String = c match
    case Unstated  => "hd-unstated"
    case Stated(_) => ""

  /** The legend's word for an unstated confidence. */
  val UnstatedLabel = "not stated"
