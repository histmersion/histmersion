package com.histmersion.domain.catalogue.periods

import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.files.BundleEntities
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.exchange.{BundleScope, MergeRules}
import com.histmersion.values.BundleKind

import com.augustnagro.magnum.DbCon
import hearth.kindlings.optics.syntax.*

/** How a merge treats named periods: the ledger names one by its id; a plan knows which of the bundle's periods with no
  * uid here are one here — by id, or by label and an overlapping span.
  */
object PeriodMergeRules extends MergeRules:
  val owner: DataOwner = DataOwner("catalogue")
  val kind: BundleKind = BundleKind.Period
  override val ledgerLabel: Option[String] = Some("SELECT id FROM periods WHERE uid = l.local_uid")

  override def gather(
      bundle: BundleEntities,
      localized: BundleEntities,
      in: MergePlanner.Input,
      settings: MergeSettings
  )(using DbCon): MergePlanner.Input =
    val ownUids = in.local.periods.map(_.uid).toSet
    val added = localized.periods.filterNot(p => ownUids(p.uid))
    val idsHere = PeriodBundleRepo.present(added.map(_.id))
    val matches = PeriodBundleRepo.matchPeriods(added.filterNot(p => idsHere(p.id)))
    val rows = PeriodBundleRepo.periods(BundleScope.All, (idsHere ++ matches.values).toList).toList
    in.modify(_.local.periods).setTo(rows).modify(_.periodMatches).setTo(matches)
