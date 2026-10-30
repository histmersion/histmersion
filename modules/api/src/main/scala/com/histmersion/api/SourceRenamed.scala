package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The answer of a rename: the source under its new citekey (without its body) and the counts. */
final case class SourceRenamed(
    oldCitekey: String,
    newCitekey: String,
    source: SourceDetail,
    counts: RenameCounts,
    /** The Calibre book id, when the original is in Calibre. Calibre never stores our citekey, so nothing there changes
      * — but the host-side originals directory is keyed by citekey.
      */
    calibreId: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SourceRenamed:
  given Schema[SourceRenamed] = summon[KindlingsSchema[SourceRenamed]].schema
