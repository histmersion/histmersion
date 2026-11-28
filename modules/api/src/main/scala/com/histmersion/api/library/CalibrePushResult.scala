package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.TwoStates

final case class CalibrePushResult(citekey: Citekey, calibreId: Source.CalibreId, ok: CalibrePushResult.Ok)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CalibrePushResult:
  enum Ok derives CanEqual:
    case Ok, Failed
  object Ok extends TwoStates[Ok]:
    extension (ok: Ok) def ok: Boolean = ok == Ok
