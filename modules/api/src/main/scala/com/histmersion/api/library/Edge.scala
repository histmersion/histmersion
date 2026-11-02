package com.histmersion.api.library
import com.histmersion.api.given
import com.histmersion.api.StrictJson
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import scala.annotation.tailrec
import sttp.tapir.Schema
final case class Edge(src: String, dst: String, kind: String)

object Edge:

  final case class Input(dst: String, kind: com.histmersion.domain.library.Edge.Kind, src: Option[String] = None)
      derives KindlingsSchema
  object Input:
    given Schema[Input] = summon[KindlingsSchema[Input]].schema

    given Codec.AsObject[Input] = StrictJson.codec(KindlingsCodecAsObject.derived[Input])
