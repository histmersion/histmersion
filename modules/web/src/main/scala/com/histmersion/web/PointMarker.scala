package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** The pure half: a point's marker markup. */
object PointMarker:
  /** Pure: the badge — shape class, the kind's classes and attributes, the fill as `--c`, the content. */
  type HtmlResult = HtmlResult.T
  object HtmlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HtmlFill = HtmlFill.T
  object HtmlFill extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SignatureResult = SignatureResult.T
  object SignatureResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AriaLabelResult = AriaLabelResult.T
  object AriaLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def html[A](k: PointKind[A], a: A, fill: com.histmersion.web.PointMarker.HtmlFill): com.histmersion.web.PointMarker.HtmlResult =
    s"""<span class="hd-point hd-badge hd-badge--${k.shape.cls} ${k.classes(a)}" style="--c:$fill"${k.attrs(a)}>${k
        .inner(a)}</span>"""

  /** Pure: what of the markup changes only by re-drawing (everything but the colour, which is set in place, so it
    * animates).
    */
  def signature[A](k: PointKind[A], a: A): com.histmersion.web.PointMarker.SignatureResult =
    s"${k.classes(a)}|${k.attrs(a)}|${k.inner(a)}|${k.title(a).getOrElse("")}"

  /** Pure: a point's accessible name: what it is, then what activating it does. */
  def ariaLabel[A](k: PointKind[A], a: A): com.histmersion.web.PointMarker.AriaLabelResult = s"${k.label(a)} — ${k.action(a)}"
