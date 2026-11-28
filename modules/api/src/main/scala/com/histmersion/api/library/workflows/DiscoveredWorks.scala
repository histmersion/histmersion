package com.histmersion
package api.library.workflows

import com.histmersion.api.automation.Goal
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.CandidateSweep
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Source
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class DiscoveredWorks(
      title: Goal.Title,
      notes: Option[Goal.Notes],
      queries: List[DiscoveredWorks.Query],
      candidates: List[RankableWork],
      skipped: Map[CandidateSweep.SkippedKey, DiscoveredWorks.SkipCount],
      stored: Map[Doi, Source.Candidate.Id] = Map.empty
  ) derives JsonCodec

object DiscoveredWorks:
  type Query = Query.T
  object Query extends Newtype[String, Pure]
  type SkipCount = SkipCount.T
  object SkipCount extends Newtype[Int, Pure]
