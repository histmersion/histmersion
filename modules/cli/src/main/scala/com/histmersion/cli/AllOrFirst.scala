package com.histmersion
package cli

/** Every element through a step that can fail: all the results, or the first failure (what cats' `traverse` did for the
  * CLI's lists of `Either`).
  */
object AllOrFirst:
  extension [A](as: List[A])
    def traverse[E, B](f: A => Either[E, B]): Either[E, List[B]] =
      as.foldRight[Either[E, List[B]]](Right(Nil))((a, acc) => f(a).flatMap(b => acc.map(b :: _)))
