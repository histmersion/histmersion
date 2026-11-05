package com.histmersion.util.database
enum Param:
  case S(v: S.V)
  case L(v: L.V)
  case I(v: I.V)
  case D(v: D.V)

  /** A `text[]` parameter — bound as a real SQL array, so a list crosses as ONE value. */
  case A(v: List[A.V])

object Param:
  object S:
    type V = V.T
    object V extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object L:
    type V = V.T
    object V extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object I:
    type V = V.T
    object V extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object D:
    type V = V.T
    object V extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object A:
    type V = V.T
    object V extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
