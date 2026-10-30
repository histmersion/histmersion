package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class SourceGraph(
    root: String,
    depth: Int,
    nodes: List[GraphNode],
    links: List[GraphLink]
) derives KindlingsCodecAsObject,
      KindlingsSchema

object SourceGraph:
  given Schema[SourceGraph] = summon[KindlingsSchema[SourceGraph]].schema
