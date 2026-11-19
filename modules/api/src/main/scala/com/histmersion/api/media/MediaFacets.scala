package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaFacets(providers: List[MediaCount], licences: List[MediaCount])
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
