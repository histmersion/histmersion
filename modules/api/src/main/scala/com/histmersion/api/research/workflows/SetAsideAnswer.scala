package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.research.CritiqueRef
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class SetAsideAnswer(
    @description("The id of the missing-critique finding; empty for a checklist pair no finding names (one your edits brought in)") id: SetAsideAnswer.Id,
    @description("The pair, as src>dst — null to use the one the finding names") pair: Option[SetAsideAnswer.Pair] = None,
    @description("One sentence, after reading both records: what the article cites the source for, and why the critique does not touch it") reason: SetAsideAnswer.Rationale
) derives JsonCodec, KindlingsSchema, Schema

object SetAsideAnswer:
  export ReviewFinding.Id
  export CritiqueRef.Wire as Pair
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
