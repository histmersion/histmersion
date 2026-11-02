package com.histmersion.api.aliases
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given

final case class UidRedirect(aliasUid: UUID, uid: UUID, kind: String, mergeId: Option[UUID])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object UidRedirect:
  given Schema[UidRedirect] = summon[KindlingsSchema[UidRedirect]].schema
