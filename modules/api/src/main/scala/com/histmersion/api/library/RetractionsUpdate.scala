package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** The refresh downloads ~30 MB and rewrites ~60 000 rows, far longer than one HTTP request may take, so `POST` starts
  * it and `GET` is polled until `running` is false.
  */
final case class RetractionsUpdate(
    rows: Int,
    source: String,
    running: Boolean = false,
    error: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object RetractionsUpdate:
  given Schema[RetractionsUpdate] = summon[KindlingsSchema[RetractionsUpdate]].schema
