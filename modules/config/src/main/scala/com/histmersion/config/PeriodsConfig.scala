package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Named periods (#719): where `periods fetch periodo` downloads the PeriodO dump, the authorities it imports by
  * default (comma-separated PeriodO ids) and the bundled preset seed under [[InitConfig.dataDir]].
  */
final case class PeriodsConfig(periodoUrl: PeriodsConfig.PeriodoUrl, periodoCollections: PeriodsConfig.PeriodoCollections, seedFile: PeriodsConfig.SeedFile) derives ConfigReader:
  def collections: List[String] = PeriodsConfig.split(periodoCollections)

object PeriodsConfig:
  type PeriodoUrl = PeriodoUrl.T
  object PeriodoUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodoCollections = PeriodoCollections.T
  object PeriodoCollections extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedFile = SeedFile.T
  object SeedFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private[config] def split(s: String): List[String] =
    Option(s).toList.flatMap(_.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct
