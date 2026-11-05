package com.histmersion.web

import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The "open in a new tab" action of a list row or a detail. */
object NewTabLink:
  type ApplyHref0 = ApplyHref0.T
  object ApplyHref0 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyWhat = ApplyWhat.T
  object ApplyWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(href0: com.histmersion.web.NewTabLink.ApplyHref0, what: com.histmersion.web.NewTabLink.ApplyWhat): HtmlElement =
    a(
      cls := "btn btn--bare btn--icon btn--sm new-tab",
      href := href0,
      target := "_blank",
      rel := "noopener",
      title := s"Open “$what” in a new tab",
      aria.label := s"Open $what in a new tab",
      Icon(Icon.NewTab)
    )
