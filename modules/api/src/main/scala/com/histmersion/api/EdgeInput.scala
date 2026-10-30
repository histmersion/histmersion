package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.values.EdgeKind

final case class EdgeInput(dst: String, kind: EdgeKind, src: Option[String] = None) derives KindlingsSchema
object EdgeInput:
  given Schema[EdgeInput] = summon[KindlingsSchema[EdgeInput]].schema

  given Codec.AsObject[EdgeInput] = StrictJson.codec(KindlingsCodecAsObject.derived[EdgeInput])
