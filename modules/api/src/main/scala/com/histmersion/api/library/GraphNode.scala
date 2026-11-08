package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class GraphNode(
    id: com.histmersion.api.library.Citekey,
    title: Option[com.histmersion.api.library.Title] = None,
    year: Option[com.histmersion.api.catalogue.Year] = None,
    status: Option[com.histmersion.api.library.Source.Status] = None,
    depth: GraphNode.Depth = _root_.com.histmersion.api.library.Source.Graph.DepthCount(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object GraphNode:
  export com.histmersion.api.library.Source.Graph.{DepthCount as Depth}
