package com.histmersion
package util.problem

/** One problem or several. A function that can fail for several reasons at once returns `Either[Problems, A]`; one that
  * returns `Either[Problem, A]` already conforms. The operations are on [[Problem]]'s companion (`toVector`, `++`,
  * `at`, `exists`, `fold`, `Either.and`, the tuples' `allOrProblems`), so they need no import; compare two with `==`
  * only as values of `Problems`, and never match on the union: `fold` or `toVector` it.
  */
type Problems = Problem | NonEmptyVector[Problem]

object Problems:

  /** `first`, then `more`: written with its type, so that `if … then one else many` keeps it. */
  def of(first: Problem, more: Problem*): Problems = NonEmptyVector.of(first, more*)

  /** Every problem of `all`, in order: the one problem itself when there is one, `None` when there is none. */
  def from(all: Vector[Problem]): Option[Problems] =
    NonEmptyVector.from(all).map(nev => if nev.size == 1 then nev.head else nev)

  extension [A](inputs: Iterable[A])
    /** Every input through `check`: all the values, or every problem, not just the first. */
    def traverseAll[B](check: A => Either[Problems, B]): Either[Problems, Vector[B]] =
      val checked = inputs.toVector.map(check)
      val failed: Option[Problems] = from(checked.collect { case Left(problems) => problems.toVector }.flatten)
      failed.toLeft(checked.collect { case Right(b) => b })
