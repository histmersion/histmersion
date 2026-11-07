package com.histmersion.domain.media.links

import com.histmersion.api.Write
import com.histmersion.api.media.AssetLink
import com.histmersion.domain.media.MediaState
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.Api

/** A source's figures tied to what they show (plan §1.6): what a figure depicts, where it was taken, what it
  * illustrates, whose page it is — the links listed by source, figure and target, made and removed.
  */
trait AssetLinkService:
  def list(
      citekey: Option[com.histmersion.domain.library.Citekey],
      asset: Option[com.histmersion.domain.media.assets.AssetService.AssetUid],
      targetType: Option[com.histmersion.domain.media.assets.AssetService.TargetType],
      targetId: Option[com.histmersion.domain.media.assets.AssetService.TargetId]
  ): AssetLink.List

  /** A link made; refused when the figure it names is not stored. */
  def create(in: AssetLink.Put): Either[Api.Error, AssetLink]

  /** Whether there was a link `id` to remove. */
  def delete(id: com.histmersion.domain.media.links.AssetLinkService.LinkId): Boolean

object AssetLinkService:
  type LinkId = LinkId.T
  object LinkId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): AssetLinkService = Implementation(db)

  private def named(o: Option[String]): Option[String] = o.map(_.trim).filter(_.nonEmpty)

  private def noFigure(in: AssetLink.Put): Api.Error =
    Api.Error(
      Api.Error.Message(s"No asset '${in.citekey}/${in.assetName}'"),
      Some(Api.Error.Code(Write.CodeNotFound))
    )

  final private class Implementation(db: Transactor) extends AssetLinkService:
    def list(
        citekey: Option[com.histmersion.domain.library.Citekey],
        asset: Option[com.histmersion.domain.media.assets.AssetService.AssetUid],
        targetType: Option[com.histmersion.domain.media.assets.AssetService.TargetType],
        targetId: Option[com.histmersion.domain.media.assets.AssetService.TargetId]
    ): AssetLink.List =
      val rows = db.read(
        AssetLinkRepo.list(
          citekey,
          named(asset.map(_.value)).map(com.histmersion.domain.media.assets.MediaItemRow.Uid(_)),
          named(targetType.map(_.value)).map(com.histmersion.domain.media.imports.Target.TargetType(_)),
          named(targetId.map(_.value)).map(com.histmersion.domain.media.imports.Target.Id(_))
        )
      )
      AssetLink.List.of(com.histmersion.api.RowCount(rows.size), rows, com.histmersion.api.personal.UnreadPage.Offset(0))

    def create(in: AssetLink.Put): Either[Api.Error, AssetLink] =
      db.transact:
        val exists = com.histmersion.domain.library.Citekey
          .option(in.citekey.value)
          .exists(ck => AssetLinkRepo.figureExists(ck, com.histmersion.domain.media.assets.AssetInfoRow.Name(in.assetName.value)))
        if !exists then Left(noFigure(in))
        else Right(AssetLinkRepo.insert(in))

    def delete(id: com.histmersion.domain.media.links.AssetLinkService.LinkId): Boolean = db.transact(AssetLinkRepo.delete(AssetLink.Id(id.value)))


  /** The links media holds in memory, beside its pictures (the figure a link names is looked up there). */
  final class InMemory(state: AtomicReference[MediaState]) extends AssetLinkService:
    def list(
        citekey: Option[com.histmersion.domain.library.Citekey],
        asset: Option[com.histmersion.domain.media.assets.AssetService.AssetUid],
        targetType: Option[com.histmersion.domain.media.assets.AssetService.TargetType],
        targetId: Option[com.histmersion.domain.media.assets.AssetService.TargetId]
    ): AssetLink.List =
      val (ck, an, tt, ti) =
        (named(citekey.map(_.value)), named(asset.map(_.value)), named(targetType.map(_.value)), named(targetId.map(_.value)))
      val rows = state.get.assetLinks
        .filter(l =>
          ck.forall(_ == l.citekey.value) && an.forall(_ == l.assetName.value) && tt.forall(_ == l.targetType.value) &&
            ti.forall(_ == l.targetId.value)
        )
        .sortBy[Long](_.id.value)
        .toList
      AssetLink.List.of(com.histmersion.api.RowCount(rows.size), rows, com.histmersion.api.personal.UnreadPage.Offset(0))

    def create(in: AssetLink.Put): Either[Api.Error, AssetLink] =
      if !state.get.assets.exists(a => a.citekey.contains(in.citekey) && a.name == in.assetName) then Left(noFigure(in))
      else
        val after = state.updateAndGet { s =>
          val id = MediaState.LastAssetLinkId(s.lastAssetLinkId.value + 1)
          val l = AssetLink(AssetLink.Id(id.value), in.citekey, in.assetName, in.relation, in.targetType, in.targetId, in.xywh, in.notes)
          s.modify(_.assetLinks).using(_ :+ l).modify(_.lastAssetLinkId).setTo(id)
        }
        Right(after.assetLinks.last)

    def delete(id: com.histmersion.domain.media.links.AssetLinkService.LinkId): Boolean =
      val before = state.getAndUpdate(_.modify(_.assetLinks).using(_.filterNot(_.id.value == id.value)))
      before.assetLinks.exists(_.id.value == id.value)

