package com.histmersion.domain.media.assets

/** A picture shown on a place, an event or a source, in a role, as the in-memory store holds it (`media_links`). */
final case class HeldMediaLink(
    assetUid: HeldMediaLink.AssetUid,
    targetType: HeldMediaLink.TargetType,
    targetId: HeldMediaLink.TargetId,
    role: HeldMediaLink.Role,
    property: Option[HeldMediaLink.Property],
    item: Option[HeldMediaLink.Item],
    rank: HeldMediaLink.Rank,
    basis: Option[HeldMediaLink.Basis]
)

object HeldMediaLink:
  type AssetUid = AssetUid.T
  object AssetUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetType = TargetType.T
  object TargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetId = TargetId.T
  object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Role = Role.T
  object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Property = Property.T
  object Property extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Item = Item.T
  object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rank = Rank.T
  object Rank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Basis = Basis.T
  object Basis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
