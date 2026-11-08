package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The acquisition list's legal re-check: on or off, the days an item's check holds, the items tried a day and a pass,
  * the minutes between passes, the share (percent) of the autopilot's shared daily legal ingest allowance it may take,
  * and the hours after which a check that got no answer is due again.
  */
final case class RecheckConfig(
    enabled: RecheckConfig.Enabled,
    everyDays: Days,
    perDay: ItemCount,
    perPass: ItemCount,
    passMinutes: Minutes,
    sharePercent: Percent,
    retryHours: Hours
) derives ConfigReader

object RecheckConfig:
  type Enabled = Enabled.T
  object Enabled extends RefinedType[Boolean, Pure]
