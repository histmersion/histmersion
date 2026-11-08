package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object Km extends RefinedType[Double, Pure]
  type Years = Years.T
  object Years extends RefinedType[Long, Pure]
  type NameWeight = NameWeight.T
  object NameWeight extends RefinedType[Double, Pure]
  type FindingWeight = FindingWeight.T
  object FindingWeight extends RefinedType[Double, Pure]
  type MinScore = MinScore.T
  object MinScore extends RefinedType[Double, Pure]
  type MinNameChars = MinNameChars.T
  object MinNameChars extends RefinedType[Int, Pure]
  type MaxCandidates = MaxCandidates.T
  object MaxCandidates extends RefinedType[Int, Pure]
  type SourcesPerName = SourcesPerName.T
  object SourcesPerName extends RefinedType[Int, Pure]
  type NearFindings = NearFindings.T
  object NearFindings extends RefinedType[Int, Pure]
  type Batch = Batch.T
  object Batch extends RefinedType[Int, Pure]
