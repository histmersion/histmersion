package com.histmersion
package web
import com.histmersion.util.problem.Problem
import com.histmersion.api.problem.Text.sentence

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import io.github.iltotore.iron.constraint.any.Pure

/** The ONE error line of a window: always announced (`role="alert"`), always the `form-error` look. */
object ErrorLine:

  def text(message: Problem): HtmlElement = p(cls := "form-error", role := "alert", message.sentence)

  /** The line for an optional message (nothing while there is none). */
  def apply(message: Signal[Option[Problem]]): Modifier[HtmlElement] = child.maybe <-- message.map(_.map(text))
