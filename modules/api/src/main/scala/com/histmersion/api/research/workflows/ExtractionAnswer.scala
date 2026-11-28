package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.research.Coverage
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.customise
import sttp.tapir.Schema.annotations.description

@customise(_.name(SName("ExtractAnswer")))
final case class ExtractionAnswer(
    @description("Does the record bear on the article's question at all?") relevant: ExtractionAnswer.Relevant,
    @description("If not relevant: why (wrong region, period or sub-problem). Otherwise what it contributes") reason: Coverage.Rationale,
    @description("Every claim of the record that bears on the question; empty if not relevant") claims: List[ClaimAnswer] = Nil
) derives JsonCodec, KindlingsSchema, Schema

object ExtractionAnswer:
  type Relevant = Relevant.T
  object Relevant extends Newtype[Boolean, Pure]
