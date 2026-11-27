package com.histmersion
package util.database

import com.augustnagro.magnum.SqlLiteral
import io.github.iltotore.iron.RefinedType
import com.histmersion.lint.{tolerated, LintRule}

/** The numbers that may be written into SQL text instead of bound: `Int`, `Long`, `Double`, and every newtype over one
  * of them. Text never qualifies, so nothing a person typed reaches a statement this way.
  */
trait SqlNumber[A]:
  /** The number as an `s"..."` writes it (`toString`): the text the statements had when they were built as strings. */
  def text(a: A): String

object SqlNumber:
  given SqlNumber[Int] = _.toString
  given SqlNumber[Long] = _.toString
  given SqlNumber[Double] = _.toString
  given [T] => (m: RefinedType.Mirror[T]) => (base: SqlNumber[m.BaseType]) => SqlNumber[T] =
    t => base.text(t.asInstanceOf[m.BaseType])

extension [A](a: A)(using n: SqlNumber[A])
  /** The number written into the statement as a literal, for the places whose planner must see the value (a map tile's
    * arithmetic, a deep-time window the partial indexes match); everything else binds its values.
    */
  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  def literal: SqlLiteral = SqlLiteral(n.text(a))
