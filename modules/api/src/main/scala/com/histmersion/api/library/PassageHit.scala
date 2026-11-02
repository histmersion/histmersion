package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.MatchSpan
import com.histmersion.api.given

final case class PassageHit(offset: Int, heading: Option[String], text: String, matches: List[MatchSpan])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PassageHit:
  given Schema[PassageHit] = summon[KindlingsSchema[PassageHit]].schema
