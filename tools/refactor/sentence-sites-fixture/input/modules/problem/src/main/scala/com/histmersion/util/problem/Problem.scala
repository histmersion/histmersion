package com.histmersion
package util.problem

/** A stand-in for the project's newtype base: what SentenceSites tells a newtype by. */
abstract class Newtype[A, C]:
  type T = A
  def apply(a: A): T = a

/** The shape of the real `util.problem.Problem`: groups are sealed traits under it, cases extend a group or it. */
sealed trait Problem

object Legacy:
  type Text = Text.T
  object Text extends Newtype[String, Any]:
    def of(thrown: Throwable): Text = Text(thrown.getMessage)

object Problem:
  type Field = Field.T
  object Field extends Newtype[String, Any]

  sealed trait Invalid extends Problem
  object Invalid:
    final case class Missing(field: Field, at: Vector[Field]) extends Invalid
    object Missing:
      def here(field: Field): Missing = Missing(field, Vector.empty)

  sealed trait Config extends Problem
  object Config:
    enum Fault:
      case Empty
      case Rejected(why: String)
    final case class Setting(key: String, fault: Fault) extends Config

  sealed trait Run extends Problem
  object Run:
    case object Interrupted extends Run
    final case class Failed(all: Vector[Problem]) extends Run

  final case class Unclassified(text: Legacy.Text) extends Problem
  object Unclassified:
    def of(thrown: Throwable): Unclassified = Unclassified(Legacy.Text.of(thrown))
