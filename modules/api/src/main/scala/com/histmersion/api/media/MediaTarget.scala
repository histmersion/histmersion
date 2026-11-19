package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MediaTarget(
    targetType: MediaTarget.TargetType,
    targetId: MediaTarget.TargetId,
    role: MediaTarget.Role,
    property: Option[MediaTarget.Property] = None,
    item: Option[MediaTarget.Item] = None,
    basis: Option[MediaTarget.Basis] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object MediaTarget:
  type TargetType = TargetType.T
  object TargetType extends Newtype[String, Pure]
  type TargetId = TargetId.T
  object TargetId extends Newtype[String, Pure]
  type Role = Role.T
  object Role extends Newtype[String, Pure]
  type Property = Property.T
  object Property extends Newtype[String, Pure]
  type Item = Item.T
  object Item extends Newtype[String, Pure]
  type Basis = Basis.T
  object Basis extends Newtype[String, Pure]
