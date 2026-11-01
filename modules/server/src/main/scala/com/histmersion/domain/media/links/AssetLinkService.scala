package com.histmersion.domain.media.links

import com.histmersion.api.{ApiError, AssetLink, AssetLinkList, AssetLinkPut, Write}
import com.histmersion.domain.media.MediaState
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference

/** A source's figures tied to what they show (plan §1.6): what a figure depicts, where it was taken, what it
  * illustrates, whose page it is — the links listed by source, figure and target, made and removed.
  */
trait AssetLinkService:
  def list(citekey: Option[String], asset: Option[String], targetType: Option[String], targetId: Option[String])
      : AssetLinkList

  /** A link made; refused when the figure it names is not stored. */
  def create(in: AssetLinkPut): Either[ApiError, AssetLink]

  /** Whether there was a link `id` to remove. */
  def delete(id: Long): Boolean

object AssetLinkService:
  def apply(db: Transactor): AssetLinkService = Implementation(db)

  private def named(o: Option[String]): Option[String] = o.map(_.trim).filter(_.nonEmpty)

  private def noFigure(in: AssetLinkPut): ApiError =
    ApiError(s"No asset '${in.citekey}/${in.assetName}'", Some(Write.CodeNotFound))

  private final class Implementation(db: Transactor) extends AssetLinkService:
    def list(citekey: Option[String], asset: Option[String], targetType: Option[String], targetId: Option[String])
        : AssetLinkList =
      val rows = db.read(AssetLinkRepo.list(named(citekey), named(asset), named(targetType), named(targetId)))
      AssetLinkList.of(rows.size, rows, 0)

    def create(in: AssetLinkPut): Either[ApiError, AssetLink] =
      db.transact:
        if !AssetLinkRepo.figureExists(in.citekey, in.assetName) then Left(noFigure(in))
        else Right(AssetLinkRepo.insert(in))

    def delete(id: Long): Boolean = db.transact(AssetLinkRepo.delete(id))

  /** The links media holds in memory, beside its pictures (the figure a link names is looked up there). */
  final class InMemory(state: AtomicReference[MediaState]) extends AssetLinkService:
    def list(citekey: Option[String], asset: Option[String], targetType: Option[String], targetId: Option[String])
        : AssetLinkList =
      val (ck, an, tt, ti) = (named(citekey), named(asset), named(targetType), named(targetId))
      val rows = state.get.assetLinks
        .filter(l =>
          ck.forall(_ == l.citekey) && an.forall(_ == l.assetName) && tt.forall(_ == l.targetType) &&
            ti.forall(_ == l.targetId)
        )
        .sortBy(_.id)
        .toList
      AssetLinkList.of(rows.size, rows, 0)

    def create(in: AssetLinkPut): Either[ApiError, AssetLink] =
      if !state.get.assets.exists(a => a.citekey.contains(in.citekey) && a.name == in.assetName) then Left(noFigure(in))
      else
        val after = state.updateAndGet { s =>
          val id = s.lastAssetLinkId + 1
          val l = AssetLink(id, in.citekey, in.assetName, in.relation, in.targetType, in.targetId, in.xywh, in.notes)
          s.modify(_.assetLinks).using(_ :+ l).modify(_.lastAssetLinkId).setTo(id)
        }
        Right(after.assetLinks.last)

    def delete(id: Long): Boolean =
      val before = state.getAndUpdate(_.modify(_.assetLinks).using(_.filterNot(_.id == id)))
      before.assetLinks.exists(_.id == id)
