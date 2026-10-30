package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/restore`. `force` is required whenever the database still holds sources — a restore is
  * `pg_restore --clean --if-exists`, i.e. it DROPS what is there.
  */
final case class RestoreRequest(file: String, force: Boolean = false) derives KindlingsCodecAsObject, KindlingsSchema
object RestoreRequest:
  given Schema[RestoreRequest] = summon[KindlingsSchema[RestoreRequest]].schema
