package com.histmersion
package web

import com.histmersion.api.{ConfidenceStyle, Html}
import com.raquo.laminar.api.L.*

import scala.scalajs.js
import com.histmersion.api.library.Confidence
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The confidence mark: the shared icon plus the colour the map's dots use for that level ([[ConfidenceStyle]]),
  * labelled for screen readers and on hover.
  */
object ConfidenceBadge:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(c: Confidence): HtmlElement =
    span(
      cls := s"conf-badge conf-${c.toString.toLowerCase}", // its colour: `.conf-badge.conf-*`
      title := ConfidenceStyle.label(c),
      svg.svg(
        svg.cls := "conf-icon",
        svg.width := "10",
        svg.height := "10",
        svg.viewBox := ConfidenceStyle.IconViewBox,
        aria.hidden := true,
        ConfidenceStyle
          .iconParts(c)
          .map(p =>
            if p.filled.value then svg.path(svg.d := p.d.value, svg.fill := "currentColor")
            else svg.path(svg.d := p.d.value, svg.fill := "none", svg.stroke := "currentColor")
          )
      ),
      span(cls := "conf-text", c.toString.toLowerCase)
    )

  /** As markup, for map popups — the server's, from [[Html.Atoms]]. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def html(c: Confidence): Html = Html.Atoms.confidenceBadge(c)
