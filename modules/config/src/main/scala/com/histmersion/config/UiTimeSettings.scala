package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The timeline's span (`ui.time`): the earliest and latest year it reaches, and the years it opens on. */
final case class UiTimeSettings(min: UiTimeSettings.Min, max: UiTimeSettings.Max, defaultFrom: UiTimeSettings.DefaultFrom, defaultTo: UiTimeSettings.DefaultTo) derives ConfigReader

object UiTimeSettings:
  type Min = Min.T
  object Min extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Max = Max.T
  object Max extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultFrom = DefaultFrom.T
  object DefaultFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultTo = DefaultTo.T
  object DefaultTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
