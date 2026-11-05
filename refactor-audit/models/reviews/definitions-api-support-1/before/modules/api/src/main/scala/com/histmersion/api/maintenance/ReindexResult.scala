package com.histmersion.api.maintenance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** Re-indexing the whole corpus takes minutes, far past any HTTP timeout, so one call does a bounded slice. */
final case class ReindexResult(sources: Int, chunks: Int, lastCitekey: Option[String], remaining: Int)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
