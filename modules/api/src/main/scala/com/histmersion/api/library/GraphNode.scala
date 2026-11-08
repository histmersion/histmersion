package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year

final case class GraphNode(
    id: Citekey,
    title: Option[Title] = None,
    year: Option[Year] = None,
    status: Option[com.histmersion.api.library.Source.Status] = None,
    depth: GraphNode.Depth = _root_.com.histmersion.api.library.Source.Graph.DepthCount(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object GraphNode:
  export Source.Graph.DepthCount as Depth
