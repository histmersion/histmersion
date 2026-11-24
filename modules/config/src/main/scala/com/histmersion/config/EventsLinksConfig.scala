package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}

@movesTo(FeaturePlace.CatalogueEvents)
final case class EventsLinksConfig(
    km: EventsLinksConfig.Km,
    years: EventsLinksConfig.Years,
    nameWeight: EventsLinksConfig.NameWeight,
    findingWeight: EventsLinksConfig.FindingWeight,
    minScore: EventsLinksConfig.MinScore,
    minNameChars: EventsLinksConfig.MinNameChars,
    maxCandidates: EventsLinksConfig.MaxCandidates,
    sourcesPerName: EventsLinksConfig.SourcesPerName,
    nearFindings: EventsLinksConfig.NearFindings,
    batch: EventsLinksConfig.Batch
) derives ConfigReader

object EventsLinksConfig:
  type Km = Km.T
  object Km extends Newtype[Double, Pure]
  type Years = Years.T
  object Years extends Newtype[Long, Pure]
  type NameWeight = NameWeight.T
  object NameWeight extends Newtype[Double, Pure]
  type FindingWeight = FindingWeight.T
  object FindingWeight extends Newtype[Double, Pure]
  type MinScore = MinScore.T
  object MinScore extends Newtype[Double, Pure]
  type MinNameChars = MinNameChars.T
  object MinNameChars extends Newtype[Int, Pure]
  type MaxCandidates = MaxCandidates.T
  object MaxCandidates extends Newtype[Int, Pure]
  type SourcesPerName = SourcesPerName.T
  object SourcesPerName extends Newtype[Int, Pure]
  type NearFindings = NearFindings.T
  object NearFindings extends Newtype[Int, Pure]
  type Batch = Batch.T
  object Batch extends Newtype[Int, Pure]
