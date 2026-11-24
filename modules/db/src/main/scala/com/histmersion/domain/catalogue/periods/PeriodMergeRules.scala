package com.histmersion
package domain.catalogue.periods

import com.histmersion.api.exchange.Bundle

import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.DataOwner
import com.histmersion.domain.catalogue.CataloguePlan
import com.histmersion.util.data.exchange.PlanRules
import com.histmersion.util.data.exchange.{BundleScope, MergeRules}

import com.augustnagro.magnum.{sql, DbCon, Frag}
import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.{LintRule, tolerated}

/** How a merge treats named periods: the ledger names one by its id; a plan knows which of the bundle's periods with no
  * uid here are one here — by id, or by label and an overlapping span.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object PeriodMergeRules extends MergeRules:

  override val planning: Option[PlanRules] = Some(CataloguePlan)
  val owner: DataOwner = DataOwner("catalogue")
  val kind: Bundle.Kind = Bundle.Kind.Period
  override val ledgerLabel: Option[Frag] = Some(sql"SELECT id FROM periods WHERE uid = l.local_uid")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def gather(
      bundle: BundleEntities,
      localized: BundleEntities,
      in: MergePlanner.Input,
      settings: MergeSettings
  )(using DbCon): MergePlanner.Input =
    val ownUids = in.local.periods.map(_.uid).toSet
    val added = localized.periods.filterNot(p => ownUids(p.uid))
    val idsHere = PeriodBundleRepo.present(added.map(p => BPeriodRow.Id(p.id.value)))
    val matches = PeriodBundleRepo.matchPeriods(added.filterNot(p => idsHere(BPeriodRow.Id(p.id.value))))
    val rows = PeriodBundleRepo.periods(BundleScope.All, (idsHere ++ matches.values).toList).toList
    in.modify(_.local.periods)
      .setTo(rows)
      .modify(_.periodMatches)
      .setTo(matches.map((uid, id) => uid -> Bundle.Period.Id(id.value)))
