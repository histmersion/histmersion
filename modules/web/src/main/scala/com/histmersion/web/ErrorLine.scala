package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** The ONE error line of a window: always announced (`role="alert"`), always the `form-error` look. */
object ErrorLine:
  def text(message: String): HtmlElement = p(cls := "form-error", role := "alert", message)

  /** The line for an optional message (nothing while there is none). */
  def apply(message: Signal[Option[String]]): Modifier[HtmlElement] = child.maybe <-- message.map(_.map(text))
