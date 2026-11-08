package com.histmersion
package domain.media.links

import com.histmersion.api.Problem
import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.Write
import com.histmersion.util.identity.AliasTable
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.media.AssetLink
import com.histmersion.api.Api
import com.histmersion.api.automation.Activity
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.api.RowCount
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.media.MediaTarget

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
    AliasKinds
      .ofTarget(Activity.Entry.TargetKind(targetType))
      .fold(id)(kind => current(kind, AliasTable.Id(id)).value)

  def list(
      citekey: Option[Citekey],
      asset: Option[AssetService.AssetUid],
      targetType: Option[AssetService.TargetType],
      targetId: Option[AssetService.TargetId]
  ): AssetLink.List =
    val target = for t <- targetType; i <- targetId yield refId(t.value, i.value)
    citekey.map(k => Citekey.option(current(AliasKinds.SourceRecords, AliasTable.Id(k.value)).value)) match
      case Some(None) => AssetLink.List.of(RowCount(0), Nil, UnreadPage.Offset(0))
      case keys       =>
        links.list(
          keys.flatten,
          asset,
          targetType,
          target.map(AssetService.TargetId(_)).orElse(targetId)
        )

  def create(in: AssetLink.Put): Either[Api.Error, AssetLink] =
    maintenance.guard(WriteGate.OperationName("create_asset_link"))(
      links.create(
        in.modify(_.citekey)
          .setTo(
            com.histmersion.api.library
              .Citekey(current(AliasKinds.SourceRecords, AliasTable.Id(in.citekey.value)).value)
          )
          .modify(_.targetId)
          .setTo(MediaTarget.TargetId(refId(in.targetType.value, in.targetId.value)))
      )
    )

  def delete(id: AssetLinkService.LinkId): Either[Api.Error, AssetLink.Deleted] =
    maintenance.guard(WriteGate.OperationName("delete_asset_link")):
      if links.delete(id) then Right(AssetLink.Deleted(AssetLink.Id(id.value), AssetLink.Deleted.Value(true)))
      else Left(Api.Error(Problem(s"No asset link #$id"), Some(Api.Error.Code(Write.CodeNotFound))))
