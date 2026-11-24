package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** Named periods (#719): where `periods fetch periodo` downloads the PeriodO dump, the authorities it imports by
  * default (comma-separated PeriodO ids) and the bundled preset seed under [[InitConfig.dataDir]].
  */
@movesTo(FeaturePlace.CataloguePeriods)
final case class PeriodsConfig(
    periodoUrl: PeriodsConfig.PeriodoUrl,
    periodoCollections: PeriodsConfig.PeriodoCollections,
    seedFile: PeriodsConfig.SeedFile
) derives ConfigReader:
  def collections: List[PeriodsConfig.Collection] = PeriodsConfig.split(periodoCollections)

object PeriodsConfig:
  type Collection = Collection.T
  object Collection extends Newtype[String, Pure]

  type PeriodoUrl = PeriodoUrl.T
  object PeriodoUrl extends Newtype[String, Pure]
  type PeriodoCollections = PeriodoCollections.T
  object PeriodoCollections extends Newtype[String, Pure]
  type SeedFile = SeedFile.T
  object SeedFile extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[config] def split(s: PeriodoCollections): List[Collection] =
    Option(s).toList.flatMap(_.value.split(",").toList).map(_.trim).filter(_.nonEmpty).distinct.map(Collection(_))
