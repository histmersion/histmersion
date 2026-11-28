package com.histmersion
package web

import com.histmersion.api.MapStack
import com.histmersion.api.MapStack
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.UiLabels
import com.histmersion.api.Geo
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Html
import com.histmersion.api.catalogue.Year

/** What one KIND of point is (docs/map-layers.md): everything the map does with a point that differs between findings,
  * sites, events and pictures, as data. [[PointLayer]] does the rest, the same way for all of them.
  */
trait PointKind[A]:
  /** What a cluster bubble counts it as. */
  def dot: DotKind
  def shape: Shape

  /** Its layer switch's icon: its shape, outlined (the toolbar, the map's key). */
  def icon: Icon

  /** Its name in the toolbar, and what one marker means in the map's key. */
  def layerLabel: UiLabels.Text
  def meaning: UiLabels.Text

  /** The marker's box, in px (the shape is drawn inside it at the same optical size for every kind). */
  def box: (PointKind.BoxPixels, PointKind.BoxPixels)

  /** The pane its markers are drawn in (above the areas). */
  def pane: MapStack.Pane = MapStack.Pane.Markers
  def key(a: A): PointKind.PointKey
  def at(a: A): (Geo.Latitude, Geo.Longitude)

  /** The badge's content: its glyph ([[Glyphs.badge]]; the exclamation mark when its type is unknown), or a count.
    */
  def inner(a: A): Html

  /** Classes beyond the shape — its tier, its basis, its period-span ring. */
  def classes(a: A): String = ""

  /** Data attributes (`data-…`), each with a leading space. */
  def attrs(a: A): Html = Html.empty

  /** Its fill for the span shown: the period colour of its dates (a gradient over several). */
  def fill(a: A, span: (Year.Astronomical, Year.Astronomical)): String

  /** What it counts for in a bubble, and its row in the bubble's list. */
  def member(a: A): Member

  /** Its accessible name, and what activating it does ("pin its card"). */
  def label(a: A): UiLabels.Text
  def action(a: A): UiLabels.Text

  /** A native tooltip, only where it says something the card does not. */
  def title(a: A): Option[UiLabels.Text] = None

  /** The card its hover shows, read at the map's year. */
  def target(a: A, asOf: Option[Year.Astronomical]): Option[Preview.Target]
  def zIndex(a: A): PointKind.ZIndex = PointKind.ZIndex(0)

  /** A click or Enter on it. */
  def activate(a: A, how: Activation): Unit

  /** Its row in a bubble's list chosen (the row given, to pin a card under); `key` its point's key in the map's cluster
    * layer (to bring it out of its bubble).
    */
  def open(key: PointKind.PointKey, a: A, row: dom.Element): Unit

  /** The client-side part of its filter (a menu the server is not asked about). */
  def keep(a: A): Boolean = true

  /** Its marker drawn alone recoloured in place for `span` (only the colour moved): the badge's `--c`. */
  def recolour(m: DomMarker, a: A, span: (Year.Astronomical, Year.Astronomical)): Unit =
    Option(m.el.querySelector(".hd-point"))
      .foreach(_.asInstanceOf[dom.html.Element].style.setProperty("--c", fill(a, span)))

object PointKind:
  type BoxPixels = BoxPixels.T
  object BoxPixels extends Newtype[Int, Pure]
  type PointKey = PointKey.T
  object PointKey extends Newtype[String, Pure]
  type ZIndex = ZIndex.T
  object ZIndex extends Newtype[Int, Pure]
