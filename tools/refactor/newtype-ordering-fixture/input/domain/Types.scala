package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type Citekey = Citekey.T
object Citekey extends Newtype[String, Not[Blank]]

type Rank = Rank.T
object Rank extends Newtype[Int, Pure]:
  given Ordering[T] = Ordering.by[T, Int](_.value)
  def top: Rank = Rank(1)

type Weight = Weight.T
object Weight extends Newtype[Double, Pure]

type Label = Label.T
object Label extends Newtype[String, Pure]:
  given scala.math.Ordering[T] = scala.math.Ordering.by[T, String](_.value)

object Holder:
  type Id = Id.T
  object Id extends Newtype[String, Pure]:
    given Ordering[T] = Ordering.String.on(_.value)
  type Seq2 = Seq2.T
  object Seq2 extends Newtype[Long, Pure]:
    extension (a: T) def next: T = Seq2(a.value + 1)
    def later(a: T, b: T): Boolean = a.value > b.value

type Legacy = Legacy.T
object Legacy extends RefinedType[String, Pure]
