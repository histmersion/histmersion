package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class GraphLink(
    source: com.histmersion.api.library.Citekey,
    target: com.histmersion.api.library.Citekey,
    kind: com.histmersion.api.library.Edge.Kind
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
