package com.histmersion
package web

import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The "open in a new tab" action of a list row or a detail. */
object NewTabLink:
  type Href = Href.T
  object Href extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Label = Label.T
  object Label extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(href0: Href, what: Label): HtmlElement =
    a(
      cls := "btn btn--bare btn--icon btn--sm new-tab",
      href := href0,
      target := "_blank",
      rel := "noopener",
      title := shown"Open “$what” in a new tab",
      aria.label := shown"Open $what in a new tab",
      Icon(Icon.NewTab)
    )
