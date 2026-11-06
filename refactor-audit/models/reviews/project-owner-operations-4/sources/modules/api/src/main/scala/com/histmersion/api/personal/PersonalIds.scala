package com.histmersion.api.personal

object PersonalIds:

  type LimitResult = LimitResult.T
  object LimitResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LimitAsked = LimitAsked.T
  object LimitAsked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val MaxLimit = 1000
  val DefaultLimit = 200

  def limit(asked: Option[com.histmersion.api.personal.PersonalIds.LimitAsked]): com.histmersion.api.personal.PersonalIds.LimitResult = _root_.com.histmersion.api.personal.PersonalIds.LimitResult(asked.filter((_).value > 0).fold(DefaultLimit)((refactorValue => math.min(refactorValue.value, MaxLimit))))
