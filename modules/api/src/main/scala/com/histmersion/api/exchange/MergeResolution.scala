package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class MergeResolution(
    issue: MergeResolution.Issue,
    decision: MergeResolution.Decision,
    text: Option[MergeResolution.Text] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MergeResolution:
  type Issue = Issue.T
  object Issue extends RefinedType[String, Pure]:
    given scala.math.Ordering[T] = scala.math.Ordering.by[T, String](_.value)
  export MergeDecision.Kind.{Wire as Decision}
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
