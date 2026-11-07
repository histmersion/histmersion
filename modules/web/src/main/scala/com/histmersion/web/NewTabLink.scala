package com.histmersion.web

import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The "open in a new tab" action of a list row or a detail. */
object NewTabLink:
  type Href = Href.T
  object Href extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(href0: com.histmersion.web.NewTabLink.Href, what: com.histmersion.web.NewTabLink.Label): HtmlElement =
    a(
      cls := "btn btn--bare btn--icon btn--sm new-tab",
      href := href0.value,
      target := "_blank",
      rel := "noopener",
      title := s"Open “$what” in a new tab",
      aria.label := s"Open $what in a new tab",
      Icon(Icon.NewTab)
    )
