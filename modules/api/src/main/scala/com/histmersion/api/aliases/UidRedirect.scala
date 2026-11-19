package com.histmersion
package api.aliases
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class UidRedirect(aliasUid: UUID, uid: UUID, kind: UidRedirect.Kind, mergeId: Option[UUID])
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object UidRedirect:
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
