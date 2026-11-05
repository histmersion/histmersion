package com.histmersion.domain.catalogue.events

import com.histmersion.api.exchange.Bundle

import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.DataOwner
import com.histmersion.domain.catalogue.CataloguePlan
import com.histmersion.util.data.exchange.PlanRules
import com.histmersion.util.data.exchange.MergeRules

import com.augustnagro.magnum.DbCon
import hearth.kindlings.optics.syntax.*

/** How a merge treats events: lines of other kinds point at them by uid, the ledger names one by its id; a plan knows
  * which of the bundle's event ids and old ids name an event here.
  */
object EventMergeRules extends MergeRules:
  type LedgerLabel = LedgerLabel.T
  object LedgerLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Referable = Referable.T
  object Referable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  override val planning: Option[PlanRules] = Some(CataloguePlan)
  val owner: DataOwner = DataOwner("catalogue")
  val kind: Bundle.Kind = Bundle.Kind.Event
  override val ledgerLabel: Option[com.histmersion.domain.catalogue.events.EventMergeRules.LedgerLabel] = Some("SELECT id FROM events WHERE uid = l.local_uid")
  override val referable: com.histmersion.domain.catalogue.events.EventMergeRules.Referable = true

  override def gather(
      bundle: BundleEntities,
      localized: BundleEntities,
      in: MergePlanner.Input,
      settings: MergeSettings
  )(using DbCon): MergePlanner.Input =
    val named = bundle.events.map(_.id) ++ bundle.eventAliases.map(_.alias)
    in.modify(_.labels.events)
      .setTo(EventBundleRepo.eventsNamed(named))
      .modify(_.labels.eventAliases)
      .setTo(EventBundleRepo.oldIdsNamed(named))
