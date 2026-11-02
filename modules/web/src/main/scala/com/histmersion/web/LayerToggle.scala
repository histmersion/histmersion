package com.histmersion.web

import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** A toggle BUTTON (never a checkbox) whose pressed state is a signal: it cannot disagree with the thing it switches,
  * because it shows that thing's state rather than keeping its own.
  */
object LayerToggle:
  def apply(icon: Icon, label: String, on: Signal[Boolean], set: Boolean => Unit): HtmlElement =
    button(
      typ := "button",
      cls := "btn btn--toggle", // the shared control look: style.css `.btn`, `.btn--toggle`
      aria.pressed <-- on.map(_.toString),
      title := label,
      Icon(icon),
      span(label),
      onClick.compose(_.sample(on)) --> (current => set(!current))
    )
