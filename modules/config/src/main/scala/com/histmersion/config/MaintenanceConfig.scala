package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion maintenance vacuum` (#1445): the tables it vacuums when none is named. */
final case class MaintenanceConfig(vacuumTables: MaintenanceConfig.VacuumTables) derives ConfigReader:
  def tables: List[com.histmersion.config.MaintenanceConfig.Table] =
    Option(vacuumTables).toList.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct

object MaintenanceConfig:
  type Table = Table.T
  object Table extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type VacuumTables = VacuumTables.T
  object VacuumTables extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
