package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class RefsResult(
    target: RefsResult.Target,
    doi: Option[Doi],
    backend: RefsResult.Backend,
    count: RefsResult.Count,
    resolved: RefsResult.Resolved,
    edgesAdded: RefsResult.EdgesAdded,
    references: List[RefRow]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object RefsResult:
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Backend = Backend.T
  object Backend extends Newtype[String, Pure]
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Resolved = Resolved.T
  object Resolved extends Newtype[Int, Pure]
  type EdgesAdded = EdgesAdded.T
  object EdgesAdded extends Newtype[Int, Pure]
