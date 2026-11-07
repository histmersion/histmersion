package com.histmersion.web

import com.raquo.laminar.api.L.*

import scala.scalajs.js
import com.histmersion.api.library.Label

/** The one chip: a readable label (never an underscore — [[Label.display]]), an optional prefix, and an optional remove
  * button. Editable chip inputs, the empty-state list, article cards and the link preview all draw theirs with it.
  */
object Chip:
  type Choice = Choice.T
  object Choice extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtraClass = ExtraClass.T
  object ExtraClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Prefix = Prefix.T
  object Prefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tip = Tip.T
  object Tip extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(
      text: com.histmersion.web.Chip.Text,
      remove: Option[() => Unit] = None,
      prefix: Option[com.histmersion.web.Chip.Prefix] = None,
      tip: Option[com.histmersion.web.Chip.Tip] = None,
      extraCls: com.histmersion.web.Chip.ExtraClass = com.histmersion.web.Chip.ExtraClass(""),
      mods: Modifier[HtmlElement]*
  ): HtmlElement =
    span(
      cls := s"chip $extraCls".trim,
      tip.map(t => title := t.value),
      prefix.map(p => span(cls := "pfx", p.value)),
      text.value,
      mods,
      remove.map(r =>
        button(
          typ := "button",
          cls := "chip-x",
          aria.label := s"remove $text",
          "✕",
          onClick.stopPropagation --> (_ => r())
        )
      )
    )

  /** A chip for a stored VALUE: the label is its display form. */
  def value(v: com.histmersion.web.Chip.Choice, remove: Option[() => Unit] = None, prefix: Option[com.histmersion.web.Chip.Prefix] = None): HtmlElement =
    apply(Chip.Text(Label.display(Label.Text(v.value)).value), remove, prefix)
