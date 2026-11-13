package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class GraphLink(
    source: Citekey,
    target: Citekey,
    kind: Edge.Kind
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema
