package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Named periods (#719): where `periods fetch periodo` downloads the PeriodO dump, the authorities it imports by
  * default (comma-separated PeriodO ids) and the bundled preset seed under [[InitConfig.dataDir]].
  */
final case class PeriodsConfig(periodoUrl: String, periodoCollections: String, seedFile: String) derives ConfigReader:
  def collections: List[String] = PeriodsConfig.split(periodoCollections)

object PeriodsConfig:
  private[config] def split(s: String): List[String] =
    Option(s).toList.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct
