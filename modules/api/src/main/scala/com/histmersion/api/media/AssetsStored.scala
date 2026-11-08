package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

final case class AssetsStored(citekey: Citekey, stored: AssetsStored.Stored) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object AssetsStored:
  type Stored = Stored.T
  object Stored extends Newtype[Int, Pure]
