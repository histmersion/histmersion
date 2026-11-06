package com.histmersion.api.aliases
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class UidRedirect(aliasUid: UUID, uid: UUID, kind: UidRedirect.Kind, mergeId: Option[UUID])
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object UidRedirect:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
