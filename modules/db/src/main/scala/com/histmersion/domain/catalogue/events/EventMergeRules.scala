package com.histmersion
package domain.catalogue.events

import com.histmersion.api.exchange.Bundle

import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.DataOwner
import com.histmersion.domain.catalogue.CataloguePlan
import com.histmersion.util.data.exchange.PlanRules
import com.histmersion.util.data.exchange.MergeRules

import com.augustnagro.magnum.{sql, DbCon, Frag}
import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.{tolerated, LintRule}

/** How a merge treats events: lines of other kinds point at them by uid, the ledger names one by its id; a plan knows
  * which of the bundle's event ids and old ids name an event here.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object EventMergeRules extends MergeRules:

  override val planning: Option[PlanRules] = Some(CataloguePlan)
  val owner: DataOwner = DataOwner("catalogue")
  val kind: Bundle.Kind = Bundle.Kind.Event
  override val ledgerLabel: Option[Frag] = Some(sql"SELECT id FROM events WHERE uid = l.local_uid")
  override val referable: Boolean = true

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def gather(
      bundle: BundleEntities,
      localized: BundleEntities,
      in: MergePlanner.Input,
      settings: MergeSettings
  )(using DbCon): MergePlanner.Input =
    val named = (bundle.events.map(_.id) ++ bundle.eventAliases.map(_.alias)).map(id => BEventRow.Id(id.value))
    in.modify(_.labels.events)
      .setTo(EventBundleRepo.eventsNamed(named).map((id, uid) => Bundle.Event.Id(id.value) -> uid))
      .modify(_.labels.eventAliases)
      .setTo(EventBundleRepo.oldIdsNamed(named).map((id, uid) => Bundle.EventAlias.Alias(id.value) -> uid))
