package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class AttestationDeleted(
    id: Long,
    deleted: Boolean
) derives KindlingsCodecAsObject,
      KindlingsSchema

object AttestationDeleted:
  given Schema[AttestationDeleted] = summon[KindlingsSchema[AttestationDeleted]].schema
