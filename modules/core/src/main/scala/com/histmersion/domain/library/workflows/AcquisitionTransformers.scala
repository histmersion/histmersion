package com.histmersion
package domain.library.workflows

import com.histmersion.util.json.JsonCodec
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.api.library.workflows as wire

object AcquisitionTransformers extends TwinTransformers:
  val rankingAnswer: JsonCodec[AcquisitionSweepRules.Ranking] = answer[AcquisitionSweepRules.Ranking, wire.Ranking]
  val candidateRecord: JsonCodec[AcquisitionSweepRules.Candidate] =
    answer[AcquisitionSweepRules.Candidate, wire.RankableWork]
