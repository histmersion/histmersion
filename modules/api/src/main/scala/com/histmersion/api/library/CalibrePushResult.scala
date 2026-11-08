package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class CalibrePushResult(citekey: Citekey, calibreId: Source.CalibreId, ok: CalibrePushResult.Ok)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CalibrePushResult:
  type Ok = Ok.T
  object Ok extends Newtype[Boolean, Pure]
