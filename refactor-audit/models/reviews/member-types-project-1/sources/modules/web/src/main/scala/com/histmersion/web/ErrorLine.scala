package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** The ONE error line of a window: always announced (`role="alert"`), always the `form-error` look. */
object ErrorLine:
  type TextMessage = TextMessage.T
  object TextMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyMessage = ApplyMessage.T
  object ApplyMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def text(message: com.histmersion.web.ErrorLine.TextMessage): HtmlElement = p(cls := "form-error", role := "alert", message)

  /** The line for an optional message (nothing while there is none). */
  def apply(message: Signal[Option[com.histmersion.web.ErrorLine.ApplyMessage]]): Modifier[HtmlElement] = child.maybe <-- message.map(_.map(text))
