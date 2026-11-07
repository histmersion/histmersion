package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** Named periods (#719): where `periods fetch periodo` downloads the PeriodO dump, the authorities it imports by
  * default (comma-separated PeriodO ids) and the bundled preset seed under [[InitConfig.dataDir]].
  */
final case class PeriodsConfig(periodoUrl: PeriodsConfig.PeriodoUrl, periodoCollections: PeriodsConfig.PeriodoCollections, seedFile: PeriodsConfig.SeedFile) derives ConfigReader:
  def collections: List[com.histmersion.config.PeriodsConfig.Collection] = PeriodsConfig.split(periodoCollections)

object PeriodsConfig:
  type Collection = Collection.T
  object Collection extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type PeriodoUrl = PeriodoUrl.T
  object PeriodoUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodoCollections = PeriodoCollections.T
  object PeriodoCollections extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedFile = SeedFile.T
  object SeedFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private[config] def split(s: com.histmersion.config.PeriodsConfig.PeriodoCollections): List[com.histmersion.config.PeriodsConfig.Collection] =
    Option(s).toList.flatMap(_.value.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct.map(Collection(_))
