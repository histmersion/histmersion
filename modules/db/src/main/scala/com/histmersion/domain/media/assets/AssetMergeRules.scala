package com.histmersion
package domain.media.assets

import com.histmersion.api.exchange.Bundle

import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.{MergeRules, PlanRules}

import com.augustnagro.magnum.{sql, DbCon, Frag}
import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.{tolerated, LintRule}

/** How a merge treats figures: the ledger names one by its name; other lines point at one by uid; a plan knows which
  * names the figures of the bundle's sources have here, and folds, renames and relabels them by [[AssetPlan]].
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object AssetMergeRules extends MergeRules:

  val owner: DataOwner = DataOwner("media")
  val kind: Bundle.Kind = Bundle.Kind.Asset
  override val ledgerLabel: Option[Frag] = Some(sql"SELECT name FROM assets WHERE uid = l.local_uid")
  override val referable: Boolean = true
  override val planning: Option[PlanRules] = Some(AssetPlan)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
