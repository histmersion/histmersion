package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class SourceRenamed(
    oldCitekey: String,
    newCitekey: String,
    source: SourceDetail,
    counts: RenameCounts,
    /** Calibre never stores the citekey, but the host-side originals directory is keyed by it. */
    calibreId: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SourceRenamed:
  given Schema[SourceRenamed] = summon[KindlingsSchema[SourceRenamed]].schema
