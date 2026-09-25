package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Source dependency graph (plan §1.4): D3-ready node+link structure for visualizing the citation
  * and relationship network around a source. Returned by `/api/graph/{ck}?depth=N`. */

final case class GraphNode(
    id: String,
    title: Option[String] = None,
    year: Option[Int] = None,
    status: Option[String] = None,
    depth: Int = 0
) derives KindlingsCodecAsObject

object GraphNode:
  given Schema[GraphNode] = KindlingsSchema.derived[GraphNode].schema

final case class GraphLink(
    source: String,
    target: String,
    kind: String
) derives KindlingsCodecAsObject

object GraphLink:
  given Schema[GraphLink] = KindlingsSchema.derived[GraphLink].schema

final case class SourceGraph(
    root: String,
    depth: Int,
    nodes: List[GraphNode],
    links: List[GraphLink]
) derives KindlingsCodecAsObject

object SourceGraph:
  given Schema[SourceGraph] = KindlingsSchema.derived[SourceGraph].schema
