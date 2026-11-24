package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.UiLabels
import com.histmersion.api.Html
import com.histmersion.api.Html.html as markup
import com.histmersion.lint.{LintRule, tolerated}

/** The pure half: a point's marker markup. */
object PointMarker:
  /** Pure: the badge — shape class, the kind's classes and attributes, the fill as `--c`, the content. */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def html[A](k: PointKind[A], a: A, fill: String): Html =
    markup"""<span class="hd-point hd-badge hd-badge--${k.shape.cls} ${k.classes(a)}" style="--c:$fill"${k.attrs(
        a
      )}>${k.inner(a)}</span>"""

  /** Pure: what of the markup changes only by re-drawing (everything but the colour, which is set in place, so it
    * animates).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def signature[A](k: PointKind[A], a: A): String =
    s"${k.classes(a)}|${k.attrs(a).markup}|${k.inner(a).markup}|${k.title(a).getOrElse("")}"

  /** Pure: a point's accessible name: what it is, then what activating it does. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ariaLabel[A](k: PointKind[A], a: A): UiLabels.Text = UiLabels.Text(s"${k.label(a).value} — ${k.action(a).value}")
