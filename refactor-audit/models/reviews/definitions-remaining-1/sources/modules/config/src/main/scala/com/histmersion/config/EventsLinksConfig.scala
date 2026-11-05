package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

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
  object Km extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Years = Years.T
  object Years extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NameWeight = NameWeight.T
  object NameWeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingWeight = FindingWeight.T
  object FindingWeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinScore = MinScore.T
  object MinScore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinNameChars = MinNameChars.T
  object MinNameChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxCandidates = MaxCandidates.T
  object MaxCandidates extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcesPerName = SourcesPerName.T
  object SourcesPerName extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NearFindings = NearFindings.T
  object NearFindings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Batch = Batch.T
  object Batch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
