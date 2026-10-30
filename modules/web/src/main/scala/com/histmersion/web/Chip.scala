package com.histmersion.web

import com.histmersion.api.*
import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** The one chip: a readable label (never an underscore — [[Label.display]]), an optional prefix, and an optional remove
  * button. Editable chip inputs, the empty-state list, article cards and the link preview all draw theirs with it.
  */
object Chip:
  def apply(
      text: String,
      remove: Option[() => Unit] = None,
      prefix: Option[String] = None,
      tip: Option[String] = None,
      extraCls: String = "",
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
  def value(v: String, remove: Option[() => Unit] = None, prefix: Option[String] = None): HtmlElement =
    apply(Label.display(v), remove, prefix)
