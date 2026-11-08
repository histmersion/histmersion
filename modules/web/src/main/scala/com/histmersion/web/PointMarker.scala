package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.UiLabels

/** The pure half: a point's marker markup. */
object PointMarker:
  /** Pure: the badge — shape class, the kind's classes and attributes, the fill as `--c`, the content. */

  def html[A](k: PointKind[A], a: A, fill: String): String =
    s"""<span class="hd-point hd-badge hd-badge--${k.shape.cls} ${k.classes(a)}" style="--c:$fill"${k.attrs(a)}>${k
        .inner(a)}</span>"""

  /** Pure: what of the markup changes only by re-drawing (everything but the colour, which is set in place, so it
    * animates).
    */
  def signature[A](k: PointKind[A], a: A): String =
    s"${k.classes(a)}|${k.attrs(a)}|${k.inner(a)}|${k.title(a).getOrElse("")}"

  /** Pure: a point's accessible name: what it is, then what activating it does. */
  def ariaLabel[A](k: PointKind[A], a: A): UiLabels.Text = UiLabels.Text(s"${k.label(a).value} — ${k.action(a).value}")
