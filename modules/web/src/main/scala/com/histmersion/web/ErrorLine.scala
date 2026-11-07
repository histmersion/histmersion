package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** The ONE error line of a window: always announced (`role="alert"`), always the `form-error` look. */
object ErrorLine:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def text(message: com.histmersion.web.ErrorLine.Problem): HtmlElement = p(cls := "form-error", role := "alert", message.value)

  /** The line for an optional message (nothing while there is none). */
  def apply(message: Signal[Option[com.histmersion.web.ErrorLine.Problem]]): Modifier[HtmlElement] = child.maybe <-- message.map(_.map(text))
