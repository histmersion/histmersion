package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MergeResolution(
    issue: MergeResolution.Issue,
    decision: MergeResolution.Decision,
    text: Option[MergeResolution.Text] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object MergeResolution:
  type Issue = Issue.T
  object Issue extends Newtype[String, Pure]
  export MergeDecision.Kind.Wire as Decision
  type Text = Text.T
  object Text extends Newtype[String, Pure]
