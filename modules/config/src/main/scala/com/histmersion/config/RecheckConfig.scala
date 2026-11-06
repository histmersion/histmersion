package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
