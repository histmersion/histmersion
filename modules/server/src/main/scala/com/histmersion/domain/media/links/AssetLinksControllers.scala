package com.histmersion.domain.media.links

import com.histmersion.api.*
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.identity.AliasKind

import hearth.kindlings.optics.syntax.*

/** A figure's links over REST and MCP: listed, made and removed. A citekey or a target named by an old identifier is
  * resolved first (`current`, the alias tables), as every read and write of a renamed kind is.
  */
final class AssetLinksControllers(
    links: AssetLinkService,
    current: (AliasKind, String) => String,
    maintenance: WriteGate
):

  /** The identifier a target of `targetType` answers to now. */
  private def refId(targetType: String, id: String): String =
    val kind = targetType.trim.toLowerCase(java.util.Locale.ROOT) match
      case "event"   => Some(AliasKindsAdapter.EventRecords)
      case "place"   => Some(AliasKindsAdapter.PlaceRecords)
      case "source"  => Some(AliasKindsAdapter.SourceRecords)
      case "article" => Some(AliasKindsAdapter.ArticleRecords)
      case _         => None
    kind.fold(id)(current(_, id))

  def list(
      citekey: Option[String],
      asset: Option[String],
      targetType: Option[String],
      targetId: Option[String]
  ): AssetLinkList =
    val target = for t <- targetType; i <- targetId yield refId(t, i)
    links.list(citekey.map(current(AliasKindsAdapter.SourceRecords, _)), asset, targetType, target.orElse(targetId))

  def create(in: AssetLinkPut): Either[ApiError, AssetLink] =
    maintenance.guard("create_asset_link")(
      links.create(
        in.modify(_.citekey)
          .setTo(current(AliasKindsAdapter.SourceRecords, in.citekey))
          .modify(_.targetId)
          .setTo(refId(in.targetType, in.targetId))
      )
    )

  def delete(id: Long): Either[ApiError, AssetLinkDeleted] =
    maintenance.guard("delete_asset_link"):
      if links.delete(id) then Right(AssetLinkDeleted(id, true))
      else Left(ApiError(s"No asset link #$id", Some(Write.CodeNotFound)))
