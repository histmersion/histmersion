package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** The timeline's span (`ui.time`): the earliest and latest year it reaches, and the years it opens on. */
final case class UiTimeSettings(
    min: UiTimeSettings.Min,
    max: UiTimeSettings.Max,
    defaultFrom: UiTimeSettings.DefaultFrom,
    defaultTo: UiTimeSettings.DefaultTo
) derives ConfigReader

object UiTimeSettings:
  type Min = Min.T
  object Min extends Newtype[Long, Pure]
  type Max = Max.T
  object Max extends Newtype[Long, Pure]
  type DefaultFrom = DefaultFrom.T
  object DefaultFrom extends Newtype[Long, Pure]
  type DefaultTo = DefaultTo.T
  object DefaultTo extends Newtype[Long, Pure]
