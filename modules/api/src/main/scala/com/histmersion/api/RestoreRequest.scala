package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `force` is required while the database holds sources: `pg_restore --clean` drops what is there. */
final case class RestoreRequest(file: String, force: Boolean = false) derives KindlingsCodecAsObject, KindlingsSchema
object RestoreRequest:
  given Schema[RestoreRequest] = summon[KindlingsSchema[RestoreRequest]].schema
