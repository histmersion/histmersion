package com.histmersion.web

import com.histmersion.api.*
import com.raquo.laminar.api.L.*

import scala.scalajs.js
import com.histmersion.values.Confidence

/** The confidence mark: the shared icon plus the colour the map's dots use for that level ([[ConfidenceStyle]]),
  * labelled for screen readers and on hover.
  */
object ConfidenceBadge:
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
            if p.filled then svg.path(svg.d := p.d, svg.fill := "currentColor")
            else svg.path(svg.d := p.d, svg.fill := "none", svg.stroke := "currentColor")
          )
      ),
      span(cls := "conf-text", c.toString.toLowerCase)
    )

  /** As an HTML string, for map popups — the server's markup, from [[HtmlAtoms]]. */
  def html(c: Confidence): String = HtmlAtoms.confidenceBadge(c)
