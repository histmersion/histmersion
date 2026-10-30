package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The state of the local Retraction Watch mirror.
  *
  * The refresh downloads ~30 MB and rewrites ~60 000 rows — far longer than one HTTP request may take (the same problem
  * `reindex` has), so `POST` STARTS it and returns immediately with `running = true`; poll `GET` until `running` is
  * false. `rows` is what the table holds right now.
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
