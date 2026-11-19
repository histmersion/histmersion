package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ContradictionsResult(rows: List[ContradictionRow], total: ContradictionsResult.Total)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ContradictionsResult:
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
