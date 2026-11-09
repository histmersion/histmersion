package com.histmersion
package pluginresolver

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type MavenNameRule = Match["[A-Za-z0-9_.\\-+]+"]

private[pluginresolver] def traverse[A, B](as: List[A])(
    f: A => Either[Problem, B]
): Either[Problem, List[B]] =
  as.foldRight[Either[Problem, List[B]]](Right(Nil))((a, acc) => for b <- f(a); bs <- acc yield b :: bs)
