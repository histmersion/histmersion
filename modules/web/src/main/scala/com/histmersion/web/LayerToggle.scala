package com.histmersion.web

import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** A toggle BUTTON (never a checkbox) whose pressed state is a signal: it cannot disagree with the thing it switches,
  * because it shows that thing's state rather than keeping its own.
  */
object LayerToggle:
  type ApplyLabel = ApplyLabel.T
  object ApplyLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyOn = ApplyOn.T
  object ApplyOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplySetArg1 = ApplySetArg1.T
  object ApplySetArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(icon: Icon, label: com.histmersion.web.LayerToggle.ApplyLabel, on: Signal[com.histmersion.web.LayerToggle.ApplyOn], set: com.histmersion.web.LayerToggle.ApplySetArg1 => Unit): HtmlElement =
    button(
      typ := "button",
      cls := "btn btn--toggle", // the shared control look: style.css `.btn`, `.btn--toggle`
      aria.pressed <-- on.map(_.toString),
      title := label,
      Icon(icon),
      span(label),
      onClick.compose(_.sample(on)) --> (current => set(!current))
    )
