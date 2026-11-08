package com.histmersion
package domain.catalogue.places

import com.histmersion.api.exchange.Bundle

import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.DataOwner
import com.histmersion.domain.catalogue.CataloguePlan
import com.histmersion.util.data.exchange.PlanRules
import com.histmersion.util.data.exchange.{BundleScope, MergeRules}

import com.augustnagro.magnum.DbCon
import hearth.kindlings.optics.syntax.*

/** How a merge treats places: the ledger names one by its id; a plan knows which of the bundle's place ids and old ids
  * name a place here, which of the bundle's places with no uid here are one here — by id, or by label and geometry (the
  * plan's `merge.place-*` settings) — and which of the places the bundle's events point at are here.
  */
object PlaceMergeRules extends MergeRules:

  override val planning: Option[PlanRules] = Some(CataloguePlan)
  val owner: DataOwner = DataOwner("catalogue")
  val kind: Bundle.Kind = Bundle.Kind.Place
  override val ledgerLabel: Option[String] = Some("SELECT id FROM places WHERE uid = l.local_uid")

  override def gather(
      bundle: BundleEntities,
      localized: BundleEntities,
      in: MergePlanner.Input,
      settings: MergeSettings
  )(using DbCon): MergePlanner.Input =
    // the bundle's places that have no uid here are matched by id, else by label and geometry; the rows here they
    // could be one with are read too, whatever their dataset
    val ownUids = in.local.places.map(_.uid).toSet
    val added = localized.places.filterNot(p => ownUids(p.uid))
    // …and a place of ours the bundle renamed: whether its new id is some other place's here (#888)
    val ownIds = in.local.places.map(p => p.uid -> p.id).toMap
    val renamedIds = localized.places.filter(p => ownIds.get(p.uid).exists(_ != p.id)).map(_.id)
    val idsHere = PlaceBundleRepo.present((added.map(_.id) ++ renamedIds).map(id => BPlaceRow.Id(id.value)))
    val matches = PlaceBundleRepo.matchPlaces(added.filterNot(p => idsHere(BPlaceRow.Id(p.id.value))), settings)
    val rows = PlaceBundleRepo.places(BundleScope.All, (idsHere ++ matches.values).toList).toList
    val pointedAt = (bundle.events.flatMap(_.placeId).map(id => BPlaceRow.Id(id.value)) ++ matches.values).distinct
    in.modify(_.labels.places)
      .setTo(PlaceBundleRepo.placesNamed(bundle.placeAliases.map(a => BPlaceRow.Id(a.alias.value))).map((id, uid) => Bundle.Place.Id(id.value) -> uid))
      .modify(_.labels.placeAliases)
      .setTo(PlaceBundleRepo.oldIdsNamed((bundle.places.map(_.id.value) ++ bundle.placeAliases.map(_.alias.value)).map(BPlaceRow.Id(_))).map((id, uid) => Bundle.PlaceAlias.Alias(id.value) -> uid))
      .modify(_.local.places)
      .setTo(rows)
      .modify(_.placeMatches)
      .setTo(matches.map((uid, id) => uid -> Bundle.Place.Id(id.value)))
      .modify(_.places)
      .setTo(PlaceBundleRepo.present(pointedAt).map(id => MergePlanner.Input.Places(id.value)))
