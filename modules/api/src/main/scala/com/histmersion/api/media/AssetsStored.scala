package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class AssetsStored(citekey: com.histmersion.api.library.Citekey, stored: AssetsStored.Stored) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object AssetsStored:
  type Stored = Stored.T
  object Stored extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
