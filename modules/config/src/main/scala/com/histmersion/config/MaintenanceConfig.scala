package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `histmersion maintenance vacuum` (#1445): the tables it vacuums when none is named. */
final case class MaintenanceConfig(vacuumTables: MaintenanceConfig.VacuumTables) derives ConfigReader:
  def tables: List[MaintenanceConfig.Table] =
    Option(vacuumTables).toList.flatMap(_.value.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct.map(MaintenanceConfig.Table(_))

object MaintenanceConfig:
  type Table = Table.T
  object Table extends RefinedType[String, Pure]

  type VacuumTables = VacuumTables.T
  object VacuumTables extends RefinedType[String, Pure]
