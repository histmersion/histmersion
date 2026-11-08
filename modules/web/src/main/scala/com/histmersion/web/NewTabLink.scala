package com.histmersion
package web

import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import io.github.iltotore.iron.constraint.any.Pure

/** The "open in a new tab" action of a list row or a detail. */
object NewTabLink:
  type Href = Href.T
  object Href extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]

  def apply(href0: Href, what: Label): HtmlElement =
    a(
      cls := "btn btn--bare btn--icon btn--sm new-tab",
      href := href0.value,
      target := "_blank",
      rel := "noopener",
      title := s"Open “$what” in a new tab",
      aria.label := s"Open $what in a new tab",
      Icon(Icon.NewTab)
    )
