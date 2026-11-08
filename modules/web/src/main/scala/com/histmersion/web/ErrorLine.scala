package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The ONE error line of a window: always announced (`role="alert"`), always the `form-error` look. */
object ErrorLine:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]

  def text(message: Problem): HtmlElement = p(cls := "form-error", role := "alert", message.value)

  /** The line for an optional message (nothing while there is none). */
  def apply(message: Signal[Option[Problem]]): Modifier[HtmlElement] = child.maybe <-- message.map(_.map(text))
