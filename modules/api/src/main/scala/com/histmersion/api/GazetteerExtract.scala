package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── the pinned extracts and their state (wire) ──────────────────────────────────────────────────

/** One pinned extract (`db/gazetteer/extracts.json`): the file the import reads, its checksum and size, and where a
  * missing copy may be downloaded from (`url`, empty = never).
  */
final case class GazetteerExtract(
    name: String,
    file: String,
    sha256: String,
    bytes: Long,
    features: Long,
    version: String,
    title: String,
    licence: String,
    attribution: Option[String] = None,
    url: Option[String] = None,
    built: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object GazetteerExtract:
  given Schema[GazetteerExtract] = summon[KindlingsSchema[GazetteerExtract]].schema
