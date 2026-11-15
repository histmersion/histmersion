package com.histmersion
package web
package ui

import com.raquo.laminar.api.L.*
import com.histmersion.api.WireEnumValues

/** A `<select>` over typed choices: each option shows and carries a choice's wire spelling, and the chosen option reads
  * back as the choice it spells. The DOM takes text; a choice crosses into it only as its spelling, and only a choice
  * offered here is read back, so no value is built from the DOM's text.
  */
final case class Choices[E](all: Vector[E], spelling: E => WireEnumValues.Wire):

  private def text(choice: E): String = s"${spelling(choice)}"

  /** The choice an option's text spells, when it is one offered. */
  def read(chosen: String): Option[E] = all.find(c => text(c) == chosen)

  /** The `<option>`s, one per choice, in order. */
  def options: Vector[HtmlElement] = all.map(c => option(value := text(c), text(c)))

  /** The select's value held in `choice`: shown as its spelling, and set to the choice the person picks. */
  def bound(choice: Var[E]): Modifier[HtmlElement] =
    controlled(value <-- choice.signal.map(text), onChange.mapToValue --> (t => read(t).foreach(choice.set)))

object Choices:

  /** The values `all` of a wire vocabulary, spelled as they travel. */
  def of[E](all: Vector[E])(using wire: WireEnumValues[E]): Choices[E] = Choices(all, wire.wire)

  /** No choice (spelled `none`) or one of `all`. */
  def optional[E](none: WireEnumValues.Wire, all: Vector[E])(using wire: WireEnumValues[E]): Choices[Option[E]] =
    Choices(None +: all.map(Some(_)), _.fold(none)(wire.wire))
