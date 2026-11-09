package com.histmersion
package domain.media.assets

import com.histmersion.api.exchange.Bundle
import com.histmersion.api.exchange.MergeTally

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
  private given CanEqual[UUID, UUID] = CanEqual.derived

  private val assetSource = in.foreign.assets.map(a => a.uid -> a.sourceUid).toMap
  private val linkAsset = in.foreign.assetLinks.map(l => l.uid -> l.assetUid).toMap
  private val assetLine =
    scala.collection.mutable.Map.empty[UUID, BundleEntities => Bundle.Asset]
  private val linkLine =
    scala.collection.mutable.Map.empty[UUID, BundleEntities => Bundle.AssetLink]
  private val renames = scala.collection.mutable.Map.empty[UUID, List[(Bundle.Asset.Name, Bundle.Asset.Name)]]
  private def assetIn(es: BundleEntities, u: UUID) = es.assets.find(_.uid == u).get
  private def renamed(source: UUID, from: Bundle.Asset.Name, to: Bundle.Asset.Name): Unit =
    renames(source) = renames.getOrElse(source, Nil) :+ (from -> to)

  override def ownerSource(foreign: UUID, kind: Bundle.Kind): Option[UUID] = kind match
    case Bundle.Kind.Asset     => assetSource.get(foreign)
    case Bundle.Kind.AssetLink => linkAsset.get(foreign).flatMap(assetSource.get)
    case _                     => None

  override def foldPairFirst(
      right: Bundle.Source,
      left: Bundle.Source,
      keepsBundleBody: Boolean,
      folds: PlanFolds
  ): Unit =
    // the same bytes are one figure; a name taken here gets the other collection's label
    val localAssets = in.local.assets.filter(_.sourceUid == left.uid)
    val names = scala.collection.mutable.Set.from(localAssets.map(_.name))
    val usedLocal = scala.collection.mutable.Set.empty[UUID]
    in.foreign.assets
      .filter(a => a.sourceUid == right.uid && !folds.here(folds.toLocal(a.uid)))
      .sortBy(_.name.value)
      .foreach { a =>
        localAssets.find(x => x.sha256 == a.sha256 && !usedLocal(x.uid)) match
          case Some(x) =>
            usedLocal += x.uid
            folds.fold(a.uid, x.uid, Bundle.Kind.Asset)
            assetLine(x.uid) = _ => x
            folds.count(_.modify(_.assetsMerged).using(n => MergeTally.AssetsMerged(n.value + 1)))
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
        val target =
          Bundle.AssetLink.Target.of(k.targetUid.map(u => folds.foldedTo(u).getOrElse(folds.toLocal(u))), k.targetId)
        in.local.assetLinks
          .find(x =>
            x.assetUid == here && x.relation == k.relation &&
              x.targetType == k.targetType && x.target == target
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
            PlanRelabels.InfoLine(
              s"asset ${a.name}: that name is another figure's in its source here — the bundle's arrives as $n"
            )
          )
          relabels.relabelled(PlanRelabels.RelabelledLine(s"asset ${a.name} → $n"))
      }

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities =
    es.modify(_.assets)
      .setTo(
        MergePlanner.replaced(es0.assets, _.uid, assetLine, es0, (_: Bundle.Asset) => true)
      )
      .modify(_.assetLinks)
      .setTo(
        MergePlanner
          .replaced(es0.assetLinks, _.uid, linkLine, es0, (_: Bundle.AssetLink) => true)
      )

  override def bodyRenames: Map[UUID, List[(Bundle.Asset.Name, Bundle.Asset.Name)]] = renames.toMap

object AssetPlan extends PlanRules:

  def start(in: MergePlanner.Input): PlanRun = AssetPlan(in)

  /** An asset name not taken in the survivor: `<stem>-<label>.<ext>` (then `-2`, `-3`…). `~` of the design is not
    * link-safe in Markdown, so a hyphen.
    */
  def freeName(
      name: Bundle.Asset.Name,
      label: MergePlanner.Row.Label,
      taken: Set[Bundle.Asset.Name]
  ): Bundle.Asset.Name =
    val raw = name.value
    val dot = raw.lastIndexOf('.')
    val (stem, ext) = if dot > 0 then (raw.take(dot), raw.drop(dot)) else (raw, "")
    val tag = SafeName.of(label.value)
    Iterator
      .from(1)
      .map(i => Bundle.Asset.Name(s"$stem-$tag${if i == 1 then "" else s"-$i"}$ext"))
      .find(n => !taken(n))
      .get
