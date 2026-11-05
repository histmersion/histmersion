package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class TableSize(name: TableSize.Name, bytes: com.histmersion.api.ByteCount) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object TableSize:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
