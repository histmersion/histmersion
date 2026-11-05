package com.histmersion.domain.media.assets

import com.histmersion.api.exchange.Bundle

import com.histmersion.domain.MergePlanner
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.{BundlePlan, PlanFolds, PlanRelabels, PlanRules, PlanRun}
import com.histmersion.api.text.SafeName

import hearth.kindlings.optics.syntax.*
import java.util.UUID

/** How a merge plan treats the figures of the sources and their links: a figure belongs to its source, a link to its
  * figure's; in a pair of sources that fold, the same bytes are one figure, a name the survivor has gets the other
  * collection's label (and a body taken from the bundle follows the new name), a link the survivor's figure already has
  * is one; a figure added under a name another figure of its source has here arrives renamed.
  */
final class AssetPlan(in: MergePlanner.Input) extends PlanRun:

  private val assetSource = in.foreign.assets.map(a => a.uid -> a.sourceUid).toMap
  private val linkAsset = in.foreign.assetLinks.map(l => l.uid -> l.assetUid).toMap
  private val assetLine =
    scala.collection.mutable.Map.empty[UUID, BundleEntities => com.histmersion.api.exchange.Bundle.Asset]
  private val linkLine =
    scala.collection.mutable.Map.empty[UUID, BundleEntities => com.histmersion.api.exchange.Bundle.AssetLink]
  private val renames = scala.collection.mutable.Map.empty[UUID, List[(String, String)]]
  private def assetIn(es: BundleEntities, u: UUID) = es.assets.find(_.uid == u).get
  private def renamed(source: UUID, from: com.histmersion.domain.media.assets.AssetPlan.RenamedFrom, to: com.histmersion.domain.media.assets.AssetPlan.RenamedTo): Unit =
    renames(source) = renames.getOrElse(source, Nil) :+ (from -> to)

  override def ownerSource(foreign: UUID, kind: Bundle.Kind): Option[UUID] = kind match
    case Bundle.Kind.Asset     => assetSource.get(foreign)
    case Bundle.Kind.AssetLink => linkAsset.get(foreign).flatMap(assetSource.get)
    case _                     => None

  override def foldPairFirst(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      keepsBundleBody: Boolean,
      folds: PlanFolds
  ): Unit =
    // the same bytes are one figure; a name taken here gets the other collection's label
    val localAssets = in.local.assets.filter(_.sourceUid == left.uid)
    val names = scala.collection.mutable.Set.from(localAssets.map(_.name))
    val usedLocal = scala.collection.mutable.Set.empty[UUID]
    in.foreign.assets
      .filter(a => a.sourceUid == right.uid && !folds.here(folds.toLocal(a.uid)))
      .sortBy(_.name)
      .foreach { a =>
        localAssets.find(x => x.sha256 == a.sha256 && !usedLocal(x.uid)) match
          case Some(x) =>
            usedLocal += x.uid
            folds.fold(a.uid, x.uid, Bundle.Kind.Asset)
            assetLine(x.uid) = _ => x
            folds.count(_.modify(_.assetsMerged).using(_ + 1))
          case None if names(a.name) =>
            val n = AssetPlan.freeName(a.name, in.fromLabel, names.toSet)
            names += n
            assetLine(a.uid) = es => assetIn(es, a.uid).modify(_.name).setTo(n)
            if keepsBundleBody then renamed(left.uid, a.name, n)
          case None => names += a.name
      }
    // the links of a folded figure that this collection's figure already has are one
    in.foreign.assetLinks.foreach { k =>
      folds.foldedTo(k.assetUid).foreach { here =>
        val target = k.targetUid.map(u => folds.foldedTo(u).getOrElse(folds.toLocal(u)).toString).getOrElse(k.targetId)
        in.local.assetLinks
          .find(x =>
            x.assetUid == here && x.relation == k.relation &&
              x.targetType == k.targetType && x.targetUid.map(_.toString).getOrElse(x.targetId) == target
          )
          .foreach { x =>
            folds.fold(k.uid, x.uid, Bundle.Kind.AssetLink)
            linkLine(x.uid) = _ => x
          }
      }
    }

  override def relabel(relabels: PlanRelabels): Unit =
    val taken = scala.collection.mutable.Set.from(in.local.assets.map(a => (a.sourceUid, a.name)))
    relabels.rows
      .filter(r =>
        r.decision == BundlePlan.Decision.Insert && r.kind == Bundle.Kind.Asset && !assetLine.contains(r.foreignUid)
      )
      .foreach { r =>
        val a = relabels.localized.assets.find(_.uid == r.uid).get
        if in.labels.assets.get((a.sourceUid, a.name)).exists(_ != a.uid) then
          val n = AssetPlan.freeName(a.name, in.fromLabel, taken.collect { case (s, x) if s == a.sourceUid => x }.toSet)
          taken += ((a.sourceUid, n))
          assetLine(r.foreignUid) = es => assetIn(es, r.foreignUid).modify(_.name).setTo(n)
          if assetSource.get(r.foreignUid).exists(relabels.writesInitially) then renamed(a.sourceUid, a.name, n)
          relabels.info(
            s"asset ${a.name}: that name is another figure's in its source here — the bundle's arrives as $n"
          )
          relabels.relabelled(s"asset ${a.name} → $n")
      }

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities =
    es.modify(_.assets)
      .setTo(
        MergePlanner.replaced(es0.assets, _.uid, assetLine, es0, (_: com.histmersion.api.exchange.Bundle.Asset) => true)
      )
      .modify(_.assetLinks)
      .setTo(
        MergePlanner
          .replaced(es0.assetLinks, _.uid, linkLine, es0, (_: com.histmersion.api.exchange.Bundle.AssetLink) => true)
      )

  override def bodyRenames: Map[UUID, List[(String, String)]] = renames.toMap

object AssetPlan extends PlanRules:
  type RenamedFrom = RenamedFrom.T
  object RenamedFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamedTo = RenamedTo.T
  object RenamedTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FreeNameResult = FreeNameResult.T
  object FreeNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FreeNameName = FreeNameName.T
  object FreeNameName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FreeNameLabel = FreeNameLabel.T
  object FreeNameLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FreeNameTaken = FreeNameTaken.T
  object FreeNameTaken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def start(in: MergePlanner.Input): PlanRun = AssetPlan(in)

  /** An asset name not taken in the survivor: `<stem>-<label>.<ext>` (then `-2`, `-3`…). `~` of the design is not
    * link-safe in Markdown, so a hyphen.
    */
  def freeName(name: com.histmersion.domain.media.assets.AssetPlan.FreeNameName, label: com.histmersion.domain.media.assets.AssetPlan.FreeNameLabel, taken: Set[com.histmersion.domain.media.assets.AssetPlan.FreeNameTaken]): com.histmersion.domain.media.assets.AssetPlan.FreeNameResult =
    val dot = name.lastIndexOf('.')
    val (stem, ext) = if dot > 0 then (name.take(dot), name.drop(dot)) else (name, "")
    val tag = SafeName.of(label)
    Iterator.from(1).map(i => s"$stem-$tag${if i == 1 then "" else s"-$i"}$ext").find(n => !taken(n)).get
