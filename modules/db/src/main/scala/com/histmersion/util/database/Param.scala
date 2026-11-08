package com.histmersion
package util.database

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
enum Param:
  case S(v: Param.S.V)
  case L(v: Param.L.V)
  case I(v: Param.I.V)
  case D(v: Param.D.V)

  /** A `text[]` parameter — bound as a real SQL array, so a list crosses as ONE value. */
  case A(v: List[Param.A.V])

object Param:
  object S:
    type V = V.T
    object V extends RefinedType[String, Pure]
  object L:
    type V = V.T
    object V extends RefinedType[Long, Pure]
  object I:
    type V = V.T
    object V extends RefinedType[Int, Pure]
  object D:
    type V = V.T
    object V extends RefinedType[Double, Pure]
  object A:
    type V = V.T
    object V extends RefinedType[String, Pure]
