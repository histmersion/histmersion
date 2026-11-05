package com.histmersion.domain.media.links

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.Write
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.media.AssetLink
import com.histmersion.api.Api

/** A figure's links over REST and MCP: listed, made and removed. A citekey or a target named by an old identifier is
  * resolved first (`current`, the alias tables), as every read and write of a renamed kind is.
  */
final class AssetLinksControllers(
    links: AssetLinkService,
    current: (AliasKind, String) => String,
    maintenance: WriteGate
):

  /** The identifier a target of `targetType` answers to now. */
  private def refId(targetType: com.histmersion.domain.media.links.AssetLinksControllers.RefIdTargetType, id: com.histmersion.domain.media.links.AssetLinksControllers.RefIdId): com.histmersion.domain.media.links.AssetLinksControllers.RefIdResult =
    AliasKinds.ofTarget(targetType).fold(id)(current(_, id))

  def list(
      citekey: Option[com.histmersion.domain.library.Citekey],
      asset: Option[com.histmersion.domain.media.links.AssetLinksControllers.ListAsset],
      targetType: Option[com.histmersion.domain.media.links.AssetLinksControllers.ListTargetType],
      targetId: Option[com.histmersion.domain.media.links.AssetLinksControllers.ListTargetId]
  ): AssetLink.List =
    val target = for t <- targetType; i <- targetId yield refId(t, i)
    links.list(citekey.map(current(AliasKinds.SourceRecords, _)), asset, targetType, target.orElse(targetId))

  def create(in: AssetLink.Put): Either[Api.Error, AssetLink] =
    maintenance.guard("create_asset_link")(
      links.create(
        in.modify(_.citekey)
          .setTo(current(AliasKinds.SourceRecords, in.citekey))
          .modify(_.targetId)
          .setTo(refId(in.targetType, in.targetId))
      )
    )

  def delete(id: com.histmersion.domain.media.links.AssetLinksControllers.DeleteId): Either[Api.Error, AssetLink.Deleted] =
    maintenance.guard("delete_asset_link"):
      if links.delete(id) then Right(AssetLink.Deleted(id, true))
      else Left(Api.Error(s"No asset link #$id", Some(Write.CodeNotFound)))

object AssetLinksControllers:
  type RefIdResult = RefIdResult.T
  object RefIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefIdTargetType = RefIdTargetType.T
  object RefIdTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefIdId = RefIdId.T
  object RefIdId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListAsset = ListAsset.T
  object ListAsset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListTargetType = ListTargetType.T
  object ListTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListTargetId = ListTargetId.T
  object ListTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteId = DeleteId.T
  object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
