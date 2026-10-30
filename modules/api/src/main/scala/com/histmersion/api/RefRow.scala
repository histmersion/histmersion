package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── refs (Crossref reference list → `references` edges) ────────────────────────────────────────

final case class RefRow(
    title: Option[String] = None,
    authors: List[String] = Nil,
    year: Option[Int] = None,
    doi: Option[String] = None,
    raw: Option[String] = None,
    /** The stored source this reference resolved to, when it is already in the corpus. */
    citekey: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object RefRow:
  given Schema[RefRow] = summon[KindlingsSchema[RefRow]].schema
