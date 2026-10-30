package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A source with its findings (the `get_source` response). */
final case class SourceWithFindings(
    source: SourceDetail,
    findings: List[FindingRow],
    /** Formatting problems of the summary and notes — filled on a write (put/patch), so the agent that wrote them is
      * told at once; empty on a plain read.
      */
    formatting: List[FormatIssue] = Nil,
    /** The picture a hover card shows for the source (#1446): a book's cover, else one of its own figures chosen to
      * illustrate it — the local thumbnail, with its credit.
      */
    illustration: Option[MediaThumb] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SourceWithFindings:
  given Schema[SourceWithFindings] = summon[KindlingsSchema[SourceWithFindings]].schema
