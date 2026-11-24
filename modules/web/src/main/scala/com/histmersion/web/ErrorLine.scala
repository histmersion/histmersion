package com.histmersion
package web
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.raquo.laminar.api.L.*
import com.histmersion.api.Html
import com.histmersion.api.Html.html
import org.scalajs.dom
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The ONE error line of a window: always announced (`role="alert"`), always the `form-error` look. */
object ErrorLine:

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def text(message: Problem): HtmlElement = p(cls := "form-error", role := "alert", message.sentence)

  /** The line for an optional message (nothing while there is none). */
  def apply(message: Signal[Option[Problem]]): Modifier[HtmlElement] = child.maybe <-- message.map(_.map(text))

  /** What a request's state says besides its answer: each problem the server sent, or that the server could not be
    * reached (what was shown before stays); nothing while it loads or once it answered.
    */
  def of(loaded: Signal[Loaded[?]]): Modifier[HtmlElement] = children <-- loaded.map(lines)

  /** [[of]] for several requests of one view: each sentence once (five panels of an unreachable server, one line). */
  def ofAll(loaded: Seq[Signal[Loaded[?]]]): Modifier[HtmlElement] =
    children <-- Signal
      .combineSeq(loaded)
      .map(_.toList.flatMap(said).distinct.map(s => p(cls := "form-error", role := "alert", s)))

  /** The same lines as markup, for content that is not a Laminar element (a server page in a window). */
  def markup(said: List[String]): Html =
    Html.concat(said.map(s => html"""<p class="form-error" role="alert">$s</p>"""))

  /** The lines of [[of]]. */
  def lines(loaded: Loaded[?]): List[HtmlElement] = said(loaded).map(s => p(cls := "form-error", role := "alert", s))

  /** What [[of]] says, sentence by sentence. Pure. */
  def said(loaded: Loaded[?]): List[String] = loaded match
    case Loaded.Failed(_, error)  => error.problems.sentences.toList
    case Loaded.Unreachable(last) =>
      List(
        if last.isDefined then "The server could not be reached: showing what was loaded before."
        else "The server could not be reached."
      )
    case _ => Nil
