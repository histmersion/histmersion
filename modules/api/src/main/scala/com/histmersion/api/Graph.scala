package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

/** Source dependency graph (plan §1.4): D3-ready node+link structure for visualizing the citation and relationship
  * network around a source. Returned by `/api/graph/{ck}?depth=N`.
  */

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

final case class GraphLink(
    source: String,
    target: String,
    kind: String
) derives KindlingsCodecAsObject,
      KindlingsSchema

object GraphLink:
  given Schema[GraphLink] = summon[KindlingsSchema[GraphLink]].schema

final case class SourceGraph(
    root: String,
    depth: Int,
    nodes: List[GraphNode],
    links: List[GraphLink]
) derives KindlingsCodecAsObject,
      KindlingsSchema

object SourceGraph:
  given Schema[SourceGraph] = summon[KindlingsSchema[SourceGraph]].schema
