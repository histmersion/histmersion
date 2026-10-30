package com.histmersion.web

import com.histmersion.api.*
import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** A source's standing, the one status code everywhere: a mark whose colour AND shape say the status (the stylesheet's
  * `.status-badge.st-<status>`) and the word ([[Wording.status]]); the stored value on hover.
  */
object StatusBadge:
  def apply(s: com.histmersion.values.SourceStatus): HtmlElement =
    span(cls := s"status-badge st-${s.wire}", title := s"Status: ${Wording.status(s)} (${s.wire})", Wording.status(s))
