package com.histmersion
package web

import com.histmersion.api.Wording
import com.raquo.laminar.api.L.*

import scala.scalajs.js
import com.histmersion.api.library.Source

/** A source's standing, the one status code everywhere: a mark whose colour AND shape say the status (the stylesheet's
  * `.status-badge.st-<status>`) and the word ([[Wording.status]]); the stored value on hover.
  */
object StatusBadge:
  def apply(s: Source.Status): HtmlElement =
    span(cls := s"status-badge st-${s.wire}", title := s"Status: ${Wording.status(s)} (${s.wire})", Wording.status(s))
