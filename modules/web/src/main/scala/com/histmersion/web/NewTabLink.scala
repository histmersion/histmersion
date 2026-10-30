package com.histmersion.web

import com.histmersion.api.*
import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The "open in a new tab" action of a list row or a detail. */
object NewTabLink:
  def apply(href0: String, what: String): HtmlElement =
    a(
      cls := "btn btn--bare btn--icon btn--sm new-tab",
      href := href0,
      target := "_blank",
      rel := "noopener",
      title := s"Open “$what” in a new tab",
      aria.label := s"Open $what in a new tab",
      Icon(Icon.NewTab)
    )
