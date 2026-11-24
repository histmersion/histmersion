package com.histmersion

import io.github.iltotore.iron.{RefinedType, RuntimeConstraint}

trait Newtype[A, C](using RuntimeConstraint[A, C]) extends RefinedType[A, C]:

  inline given (base: Ordering[A]) => Ordering[T] = assumeAll[Ordering](base)

  extension (x: T) inline def <(y: T)(using ordering: Ordering[T]): Boolean = ordering.lt(x, y)
