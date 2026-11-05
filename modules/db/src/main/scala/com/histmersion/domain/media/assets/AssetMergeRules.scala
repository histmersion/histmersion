package com.histmersion.domain.media.assets

import com.histmersion.api.exchange.Bundle

import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.{MergeRules, PlanRules}

import com.augustnagro.magnum.DbCon
import hearth.kindlings.optics.syntax.*

/** How a merge treats figures: the ledger names one by its name; other lines point at one by uid; a plan knows which
  * names the figures of the bundle's sources have here, and folds, renames and relabels them by [[AssetPlan]].
  */
object AssetMergeRules extends MergeRules:
  type LedgerLabel = LedgerLabel.T
  object LedgerLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Referable = Referable.T
  object Referable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val owner: DataOwner = DataOwner("media")
  val kind: Bundle.Kind = Bundle.Kind.Asset
  override val ledgerLabel: Option[com.histmersion.domain.media.assets.AssetMergeRules.LedgerLabel] = Some("SELECT name FROM assets WHERE uid = l.local_uid")
  override val referable: com.histmersion.domain.media.assets.AssetMergeRules.Referable = true
  override val planning: Option[PlanRules] = Some(AssetPlan)

  override def gather(
      bundle: BundleEntities,
      localized: BundleEntities,
      in: MergePlanner.Input,
      settings: MergeSettings
  )(using DbCon): MergePlanner.Input =
    in.modify(_.labels.assets).setTo(AssetBundleRepo.namesHere(localized.assets.map(_.sourceUid)))
