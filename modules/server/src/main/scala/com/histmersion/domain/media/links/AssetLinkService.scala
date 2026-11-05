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
      asset: Option[com.histmersion.domain.media.links.AssetLinkService.ListAsset],
      targetType: Option[com.histmersion.domain.media.links.AssetLinkService.ListTargetType],
      targetId: Option[com.histmersion.domain.media.links.AssetLinkService.ListTargetId]
  ): AssetLink.List

  /** A link made; refused when the figure it names is not stored. */
  def create(in: AssetLink.Put): Either[Api.Error, AssetLink]

  /** Whether there was a link `id` to remove. */
  def delete(id: com.histmersion.domain.media.links.AssetLinkService.DeleteId): com.histmersion.domain.media.links.AssetLinkService.DeleteResult

object AssetLinkService:
  type ListAsset = ListAsset.T
  object ListAsset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListTargetType = ListTargetType.T
  object ListTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListTargetId = ListTargetId.T
  object ListTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteResult = DeleteResult.T
  object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteId = DeleteId.T
  object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamedResult = NamedResult.T
  object NamedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamedO = NamedO.T
  object NamedO extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): AssetLinkService = Implementation(db)

  private def named(o: Option[com.histmersion.domain.media.links.AssetLinkService.NamedO]): Option[com.histmersion.domain.media.links.AssetLinkService.NamedResult] = o.map(_.trim).filter(_.nonEmpty)

  private def noFigure(in: AssetLink.Put): Api.Error =
    Api.Error(s"No asset '${in.citekey}/${in.assetName}'", Some(Write.CodeNotFound))

  final private class Implementation(db: Transactor) extends AssetLinkService:
    def list(
        citekey: Option[com.histmersion.domain.library.Citekey],
        asset: Option[com.histmersion.domain.media.links.AssetLinkService.Implementation.ListAsset],
        targetType: Option[com.histmersion.domain.media.links.AssetLinkService.Implementation.ListTargetType],
        targetId: Option[com.histmersion.domain.media.links.AssetLinkService.Implementation.ListTargetId]
    ): AssetLink.List =
      val rows = db.read(AssetLinkRepo.list(named(citekey), named(asset), named(targetType), named(targetId)))
      AssetLink.List.of(rows.size, rows, 0)

    def create(in: AssetLink.Put): Either[Api.Error, AssetLink] =
      db.transact:
        if !AssetLinkRepo.figureExists(in.citekey, in.assetName) then Left(noFigure(in))
        else Right(AssetLinkRepo.insert(in))

    def delete(id: com.histmersion.domain.media.links.AssetLinkService.Implementation.DeleteId): com.histmersion.domain.media.links.AssetLinkService.Implementation.DeleteResult = db.transact(AssetLinkRepo.delete(id))

  object Implementation:
    type ListAsset = ListAsset.T
    object ListAsset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListTargetType = ListTargetType.T
    object ListTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListTargetId = ListTargetId.T
    object ListTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteId = DeleteId.T
    object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The links media holds in memory, beside its pictures (the figure a link names is looked up there). */
  final class InMemory(state: AtomicReference[MediaState]) extends AssetLinkService:
    def list(
        citekey: Option[com.histmersion.domain.library.Citekey],
        asset: Option[com.histmersion.domain.media.links.AssetLinkService.InMemory.ListAsset],
        targetType: Option[com.histmersion.domain.media.links.AssetLinkService.InMemory.ListTargetType],
        targetId: Option[com.histmersion.domain.media.links.AssetLinkService.InMemory.ListTargetId]
    ): AssetLink.List =
      val (ck, an, tt, ti) = (named(citekey), named(asset), named(targetType), named(targetId))
      val rows = state.get.assetLinks
        .filter(l =>
          ck.forall(_ == l.citekey) && an.forall(_ == l.assetName) && tt.forall(_ == l.targetType) &&
            ti.forall(_ == l.targetId)
        )
        .sortBy(_.id)
        .toList
      AssetLink.List.of(rows.size, rows, 0)

    def create(in: AssetLink.Put): Either[Api.Error, AssetLink] =
      if !state.get.assets.exists(a => a.citekey.contains(in.citekey) && a.name == in.assetName) then Left(noFigure(in))
      else
        val after = state.updateAndGet { s =>
          val id = s.lastAssetLinkId + 1
          val l = AssetLink(id, in.citekey, in.assetName, in.relation, in.targetType, in.targetId, in.xywh, in.notes)
          s.modify(_.assetLinks).using(_ :+ l).modify(_.lastAssetLinkId).setTo(id)
        }
        Right(after.assetLinks.last)

    def delete(id: com.histmersion.domain.media.links.AssetLinkService.InMemory.DeleteId): com.histmersion.domain.media.links.AssetLinkService.InMemory.DeleteResult =
      val before = state.getAndUpdate(_.modify(_.assetLinks).using(_.filterNot(_.id == id)))
      before.assetLinks.exists(_.id == id)

  object InMemory:
    type ListAsset = ListAsset.T
    object ListAsset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListTargetType = ListTargetType.T
    object ListTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListTargetId = ListTargetId.T
    object ListTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteId = DeleteId.T
    object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
