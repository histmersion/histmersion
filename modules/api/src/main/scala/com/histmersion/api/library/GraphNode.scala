package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class GraphNode(
    id: String,
    title: Option[String] = None,
    year: Option[Int] = None,
    status: Option[com.histmersion.domain.library.Source.Status] = None,
    depth: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema

object GraphNode:
  given Schema[GraphNode] = summon[KindlingsSchema[GraphNode]].schema
