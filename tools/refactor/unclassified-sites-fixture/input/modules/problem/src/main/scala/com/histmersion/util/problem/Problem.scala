package com.histmersion
package util.problem

/** A stand-in for the project's newtype base. */
abstract class Newtype[A, C]:
  type T = A
  def apply(a: A): T = a

/** The shape of the real `util.problem.Problem` that UnclassifiedSites reads: `Unclassified`, `Legacy.Text`, the
  * temporary `legacyText` and the renderer's `sentence`.
  */
sealed trait Problem

object Legacy:
  type Text = Text.T
  object Text extends Newtype[String, Any]:
    def of(thrown: Throwable): Text = Text(thrown.getMessage)

object Problem:
  final case class Unclassified(text: Legacy.Text) extends Problem
  object Unclassified:
    def of(thrown: Throwable): Unclassified = Unclassified(Legacy.Text.of(thrown))

  extension (problem: Problem)
    def legacyText: String = problem match
      case Unclassified(text) => s"$text"

object Text:
  extension (problem: Problem) def sentence: String = problem.legacyText
