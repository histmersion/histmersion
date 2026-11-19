package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year

final case class GraphNode(
    id: Citekey,
    title: Option[Title] = None,
    year: Option[Year] = None,
    status: Option[Source.Status] = None,
    depth: GraphNode.Depth = Source.Graph.DepthCount(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object GraphNode:
  export Source.Graph.DepthCount as Depth
