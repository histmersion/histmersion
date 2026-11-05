package com.histmersion.web

import com.raquo.laminar.api.L.*

import scala.scalajs.js
import com.histmersion.api.library.Label

/** The one chip: a readable label (never an underscore — [[Label.display]]), an optional prefix, and an optional remove
  * button. Editable chip inputs, the empty-state list, article cards and the link preview all draw theirs with it.
  */
object Chip:
  type ApplyText = ApplyText.T
  object ApplyText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPrefix = ApplyPrefix.T
  object ApplyPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyTip = ApplyTip.T
  object ApplyTip extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyExtraCls = ApplyExtraCls.T
  object ApplyExtraCls extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValueV = ValueV.T
  object ValueV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValuePrefix = ValuePrefix.T
  object ValuePrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(
      text: com.histmersion.web.Chip.ApplyText,
      remove: Option[() => Unit] = None,
      prefix: Option[com.histmersion.web.Chip.ApplyPrefix] = None,
      tip: Option[com.histmersion.web.Chip.ApplyTip] = None,
      extraCls: com.histmersion.web.Chip.ApplyExtraCls = "",
      mods: Modifier[HtmlElement]*
  ): HtmlElement =
    span(
      cls := s"chip $extraCls".trim,
      tip.map(title := _),
      prefix.map(p => span(cls := "pfx", p)),
      text,
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
  def value(v: com.histmersion.web.Chip.ValueV, remove: Option[() => Unit] = None, prefix: Option[com.histmersion.web.Chip.ValuePrefix] = None): HtmlElement =
    apply(Label.display(v), remove, prefix)
