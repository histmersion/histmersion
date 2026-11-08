package com.histmersion
package domain.media.assets

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

  val owner: DataOwner = DataOwner("media")
  val kind: Bundle.Kind = Bundle.Kind.Asset
  override val ledgerLabel: Option[String] = Some("SELECT name FROM assets WHERE uid = l.local_uid")
  override val referable: Boolean = true
  override val planning: Option[PlanRules] = Some(AssetPlan)

  override def gather(
      bundle: BundleEntities,
      localized: BundleEntities,
      in: MergePlanner.Input,
      settings: MergeSettings
  )(using DbCon): MergePlanner.Input =
    in.modify(_.labels.assets)
      .setTo(
        AssetBundleRepo
          .namesHere(localized.assets.map(_.sourceUid))
          .map((key, uid) => (key._1, Bundle.Asset.Name(key._2.value)) -> uid)
      )
