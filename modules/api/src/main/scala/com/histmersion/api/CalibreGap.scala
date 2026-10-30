package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── Calibre (host-side; the server only reports which sources are missing from Calibre) ────────

/** A stored source that is NOT in Calibre yet, with the metadata `calibredb add` needs. */
final case class CalibreGap(
    citekey: String,
    title: String,
    authors: List[String],
    doi: Option[String],
    year: Option[Int],
    venue: Option[String],
    summary: Option[String],
    topics: List[String],
    provenance: Option[String],
    /** The entry this source already has in Calibre (`calibre update` rewrites it); none for a gap. */
    calibreId: Option[String] = None,
    /** An ISBN the corpus knows for the work (`source_identifiers`), for the Calibre identifiers. */
    isbn: Option[String] = None,
    /** Whether the server stores the source's original (`GET /api/sources/{ck}/original` serves it), which is where a
      * host-side `calibre push`/`sync` takes the file from. Set by the gaps listing; None where the server does not say
      * (the push metadata, an older server): the push then tries.
      */
    storedOriginal: Option[Boolean] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CalibreGap:
  given Schema[CalibreGap] = summon[KindlingsSchema[CalibreGap]].schema
