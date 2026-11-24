package com.histmersion
package web

import com.raquo.laminar.api.L.*

import scala.scalajs.js
import com.histmersion.api.library.Label
import io.github.iltotore.iron.constraint.any.Pure

/** The one chip: a readable label (never an underscore — [[Label.display]]), an optional prefix, and an optional remove
  * button. Editable chip inputs, the empty-state list, article cards and the link preview all draw theirs with it.
  */
object Chip:
  type Choice = Choice.T
  object Choice extends Newtype[String, Pure]
  type ExtraClass = ExtraClass.T
  object ExtraClass extends Newtype[String, Pure]
  type Prefix = Prefix.T
  object Prefix extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Text = Text.T
  object Text extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Tip = Tip.T
  object Tip extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  def apply(
      text: Text,
      remove: Option[() => Unit] = None,
      prefix: Option[Prefix] = None,
      tip: Option[Tip] = None,
      extraCls: ExtraClass = ExtraClass(""),
      mods: Modifier[HtmlElement]*
  ): HtmlElement =
    span(
      cls := s"chip $extraCls".trim,
      tip.map(t => title := t),
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
  def value(v: Choice, remove: Option[() => Unit] = None, prefix: Option[Prefix] = None): HtmlElement =
    apply(Text(Label.display(Label.Text(v.value)).value), remove, prefix)
