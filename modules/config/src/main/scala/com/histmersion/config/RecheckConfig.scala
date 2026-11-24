package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import RecheckConfig.{Failures, Kept}
import com.histmersion.lint.{FeaturePlace, movesTo}

/** The acquisition list's legal re-check: on or off, the days an item's check holds, the items tried a day and a pass,
  * the minutes between passes, the share (percent) of the autopilot's shared daily legal ingest allowance it may take,
  * and the hours after which a check that got no answer is due again.
  */
@movesTo(FeaturePlace.LibraryAcquisition)
final case class RecheckConfig(
    enabled: RecheckConfig.Enabled,
    everyDays: Days,
    perDay: ItemCount,
    perPass: ItemCount,
    passMinutes: Minutes,
    sharePercent: Percent,
    retryHours: Hours,
    giveUpChecks: Failures,
    giveUpDays: Days,
    attemptsKept: Kept
) derives ConfigReader

object RecheckConfig:
  type Enabled = Enabled.T
  object Enabled extends Newtype[Boolean, Pure]
  type Failures = Failures.T
  object Failures extends Newtype[Int, Pure]
  type Kept = Kept.T
  object Kept extends Newtype[Int, Pure]
