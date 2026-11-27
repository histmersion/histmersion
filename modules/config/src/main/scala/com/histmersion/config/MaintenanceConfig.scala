package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** `histmersion maintenance vacuum` (#1445): the tables it vacuums when none is named. */
@movesTo(FeaturePlace.UtilDataMaintenance)
final case class MaintenanceConfig(vacuumTables: MaintenanceConfig.VacuumTables) derives ConfigReader:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tables: List[MaintenanceConfig.Table] =
    Option(vacuumTables).toList
      .flatMap(_.value.split(",").toList)
      .map(_.trim)
      .filter(_.nonEmpty)
      .distinct
      .map(MaintenanceConfig.Table(_))

object MaintenanceConfig:
  type Table = Table.T
  object Table extends Newtype[String, Pure]

  type VacuumTables = VacuumTables.T
  object VacuumTables extends Newtype[String, Pure]
