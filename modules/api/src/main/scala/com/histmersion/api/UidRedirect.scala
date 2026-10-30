package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID

/** A merged-away uid and the record here it redirects to (`uid_aliases`). */
final case class UidRedirect(aliasUid: UUID, uid: UUID, kind: String, mergeId: Option[UUID])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object UidRedirect:
  given Schema[UidRedirect] = summon[KindlingsSchema[UidRedirect]].schema
