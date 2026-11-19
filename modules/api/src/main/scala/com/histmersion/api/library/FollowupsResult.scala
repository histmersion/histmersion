package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class FollowupsResult(
    target: FollowupsResult.Target,
    doi: Option[Doi],
    backend: FollowupsResult.Backend,
    count: FollowupsResult.Count,
    edgesAdded: FollowupsResult.EdgesAdded,
    followups: List[FollowupRow]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object FollowupsResult:
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Backend = Backend.T
  object Backend extends Newtype[String, Pure]
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type EdgesAdded = EdgesAdded.T
  object EdgesAdded extends Newtype[Int, Pure]
