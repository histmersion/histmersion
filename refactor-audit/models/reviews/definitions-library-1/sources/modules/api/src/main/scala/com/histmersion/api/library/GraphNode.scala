package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class GraphNode(
    id: com.histmersion.api.library.Citekey,
    title: Option[com.histmersion.api.library.Title] = None,
    year: Option[com.histmersion.api.catalogue.Year] = None,
    status: Option[com.histmersion.api.library.Source.Status] = None,
    depth: GraphNode.Depth = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object GraphNode:
  type Depth = Depth.T
  object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
