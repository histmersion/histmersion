package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Named periods (#719): where `periods fetch periodo` downloads the PeriodO dump, the authorities it imports by
  * default (comma-separated PeriodO ids) and the bundled preset seed under [[InitConfig.dataDir]].
  */
final case class PeriodsConfig(periodoUrl: PeriodsConfig.PeriodoUrl, periodoCollections: PeriodsConfig.PeriodoCollections, seedFile: PeriodsConfig.SeedFile) derives ConfigReader:
  def collections: List[PeriodsConfig.Collection] = PeriodsConfig.split(periodoCollections)

object PeriodsConfig:
  type Collection = Collection.T
  object Collection extends RefinedType[String, Pure]

  type PeriodoUrl = PeriodoUrl.T
  object PeriodoUrl extends RefinedType[String, Pure]
  type PeriodoCollections = PeriodoCollections.T
  object PeriodoCollections extends RefinedType[String, Pure]
  type SeedFile = SeedFile.T
  object SeedFile extends RefinedType[String, Pure]

  private[config] def split(s: PeriodoCollections): List[Collection] =
    Option(s).toList.flatMap(_.value.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct.map(Collection(_))
