package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

final case class GraphNode(
    id: String,
    title: Option[String] = None,
    year: Option[Int] = None,
    status: Option[SourceStatus] = None,
    depth: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema

object GraphNode:
  given Schema[GraphNode] = summon[KindlingsSchema[GraphNode]].schema
