package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `forceStatus`: with `apply`, write the verdict's status even over one that is already set (by default only
  * `unassessed` is overwritten — and a retraction always is).
  */
final case class AssessRequest(id: String, apply: Boolean = false, forceStatus: Boolean = false)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object AssessRequest:
  given Schema[AssessRequest] = summon[KindlingsSchema[AssessRequest]].schema
