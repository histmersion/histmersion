package com.histmersion
package util.database

import io.github.iltotore.iron.constraint.any.Pure
enum Param derives CanEqual:
  case S(v: Param.S.V)
  case L(v: Param.L.V)
  case I(v: Param.I.V)
  case D(v: Param.D.V)

  /** A `text[]` parameter — bound as a real SQL array, so a list crosses as ONE value. */
  case A(v: List[Param.A.V])

object Param:
  object S:
    type V = V.T
    object V extends Newtype[String, Pure]
  object L:
    type V = V.T
    object V extends Newtype[Long, Pure]
  object I:
    type V = V.T
    object V extends Newtype[Int, Pure]
  object D:
    type V = V.T
    object V extends Newtype[Double, Pure]
  object A:
    type V = V.T
    object V extends Newtype[String, Pure]
