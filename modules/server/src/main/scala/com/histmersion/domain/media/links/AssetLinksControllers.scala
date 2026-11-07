package com.histmersion.domain.media.links

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.Write
import com.histmersion.util.identity.AliasTable
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
    current: (AliasKind, AliasTable.Id) => AliasTable.Id,
    maintenance: WriteGate
):

  /** The identifier a target of `targetType` answers to now. */
  private def refId(targetType: String, id: String): String =
    AliasKinds.ofTarget(com.histmersion.api.automation.Activity.Entry.TargetKind(targetType))
      .fold(id)(kind => current(kind, AliasTable.Id(id)).value)

  def list(
      citekey: Option[com.histmersion.domain.library.Citekey],
      asset: Option[com.histmersion.domain.media.assets.AssetService.AssetUid],
      targetType: Option[com.histmersion.domain.media.assets.AssetService.TargetType],
      targetId: Option[com.histmersion.domain.media.assets.AssetService.TargetId]
  ): AssetLink.List =
    val target = for t <- targetType; i <- targetId yield refId(t.value, i.value)
    citekey.map(k =>
      com.histmersion.domain.library.Citekey.option(current(AliasKinds.SourceRecords, AliasTable.Id(k.value)).value)
    ) match
      case Some(None) => AssetLink.List.of(com.histmersion.api.RowCount(0), Nil, com.histmersion.api.personal.UnreadPage.Offset(0))
      case keys =>
        links.list(
          keys.flatten,
          asset,
          targetType,
          target.map(com.histmersion.domain.media.assets.AssetService.TargetId(_)).orElse(targetId)
        )

  def create(in: AssetLink.Put): Either[Api.Error, AssetLink] =
    maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("create_asset_link"))(
      links.create(
        in.modify(_.citekey)
          .setTo(com.histmersion.api.library.Citekey(current(AliasKinds.SourceRecords, AliasTable.Id(in.citekey.value)).value))
          .modify(_.targetId)
          .setTo(com.histmersion.api.media.MediaTarget.TargetId(refId(in.targetType.value, in.targetId.value)))
      )
    )

  def delete(id: com.histmersion.domain.media.links.AssetLinkService.LinkId): Either[Api.Error, AssetLink.Deleted] =
    maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("delete_asset_link")):
      if links.delete(id) then Right(AssetLink.Deleted(AssetLink.Id(id.value), com.histmersion.api.media.AssetLink.Deleted.Value(true)))
      else Left(Api.Error(Api.Error.Message(s"No asset link #$id"), Some(Api.Error.Code(Write.CodeNotFound))))
