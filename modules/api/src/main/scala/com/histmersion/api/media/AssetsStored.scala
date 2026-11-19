package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

final case class AssetsStored(citekey: Citekey, stored: AssetsStored.Stored)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object AssetsStored:
  type Stored = Stored.T
  object Stored extends Newtype[Int, Pure]
