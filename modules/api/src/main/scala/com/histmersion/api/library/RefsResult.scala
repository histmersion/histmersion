package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class RefsResult(
    target: RefsResult.Target,
    doi: Option[Doi],
    backend: RefsResult.Backend,
    count: RefsResult.Count,
    resolved: RefsResult.Resolved,
    edgesAdded: RefsResult.EdgesAdded,
    references: List[RefRow]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object RefsResult:
  type Target = Target.T
  object Target extends RefinedType[String, Pure]
  type Backend = Backend.T
  object Backend extends RefinedType[String, Pure]
  type Count = Count.T
  object Count extends RefinedType[Int, Pure]
  type Resolved = Resolved.T
  object Resolved extends RefinedType[Int, Pure]
  type EdgesAdded = EdgesAdded.T
  object EdgesAdded extends RefinedType[Int, Pure]
