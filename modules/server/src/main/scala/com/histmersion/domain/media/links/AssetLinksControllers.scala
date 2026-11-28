package com.histmersion
package domain.media.links

import com.histmersion.api.automation.activity.Entry
import com.histmersion.util.problem.{Legacy, Problem}

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
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** A figure's links over REST and MCP: listed, made and removed. A citekey or a target named by an old identifier is
  * resolved first (`current`, the alias tables), as every read and write of a renamed kind is.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final class AssetLinksControllers(
    links: AssetLinkService,
    current: (AliasKind, AliasTable.Id) => AliasTable.Id,
    maintenance: WriteGate
):

  /** The identifier a target of `targetType` answers to now. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def refId(targetType: String, id: String): String =
    AliasKinds
      .ofTarget(Entry.TargetKind(targetType))
      .fold(id)(kind => current(kind, AliasTable.Id(id)).value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def create(in: AssetLink.Put)(using Creator.New): Either[Api.Error, AssetLink] =
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

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def delete(id: AssetLinkService.LinkId): Either[Api.Error, AssetLink.Deleted] =
    maintenance.guard(WriteGate.OperationName("delete_asset_link")):
      if links.delete(id) then Right(AssetLink.Deleted(AssetLink.Id(id.value), AssetLink.Deleted.Value.Deleted))
      else
        Left(
          Api.Error(Problem.Unclassified(Legacy.Text(s"No asset link #$id")), Some(Api.Error.Code(Write.CodeNotFound)))
        )
