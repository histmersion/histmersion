package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion maintenance vacuum` (#1445): the tables it vacuums when none is named. */
final case class MaintenanceConfig(vacuumTables: String) derives ConfigReader:
  def tables: List[String] =
    Option(vacuumTables).toList.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct
