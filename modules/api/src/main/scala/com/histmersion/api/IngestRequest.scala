package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** @param id
  *   a DOI, `arxiv:<id>`, or a bare arXiv id
  * @param citekey
  *   override the generated citekey
  * @param arxiv
  *   an explicit arXiv id to try when the DOI has no OA copy
  */
final case class IngestRequest(
    id: String,
    citekey: Option[String] = None,
    arxiv: Option[String] = None,
    /** Fetch and convert again although the source already has its original (a stored file or a converted body) — the
      * new conversion replaces the body. Without it such a source only has its metadata and edges refreshed; one with
      * no original is always fetched.
      */
    refetch: Boolean = false,
    /** Ask the legal chain only: no fetcher the config marks unauthorised runs, whatever `acquire.allow-unauthorised`
      * says. Otherwise a person's ingest as usual — with no legal copy the source is still created from its metadata.
      */
    legalOnly: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object IngestRequest:
  given Schema[IngestRequest] = summon[KindlingsSchema[IngestRequest]].schema
