package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One passage: `offset` is where `text` starts in the body — the SAME character offset `get_source` takes, so
  * `get_source {offset}` reads on from here — `heading` the breadcrumb of the section it sits in (as the table of
  * contents shows it), and `matches` the matched spans inside `text`.
  */
final case class PassageHit(offset: Int, heading: Option[String], text: String, matches: List[MatchSpan])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PassageHit:
  given Schema[PassageHit] = summon[KindlingsSchema[PassageHit]].schema
