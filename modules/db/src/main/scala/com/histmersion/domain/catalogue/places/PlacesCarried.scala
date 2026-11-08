package com.histmersion
package domain.catalogue.places

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{BundleEntry, CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*

/** What a bundle carries of the places: this collection's own places (renamed first, as the other side renamed them),
  * their old ids, and the site types refined from outside.
  */
object PlacesCarried:
  private val Catalogue = DataOwner("catalogue")

  private def rows(rank: ImportRank)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, rank, write)

  val places: CarriedKind = CarriedKind.records[Bundle.Place](
    Catalogue,
    Bundle.Kind.Place,
    TableName("places"),
    List(rows(ImportRank(30))(PlaceBundleRepo.placeRenames), rows(ImportRank(31))(PlaceBundleRepo.placesIn))
  )(scope => PlaceBundleRepo.places(scope).toList)(
    p => CarriedKind.RecordsClock(p.updatedAt.value),
    (es, ls) => es.modify(_.places).setTo(ls)
  )

  val placeAliases: CarriedKind = CarriedKind.owned[Bundle.PlaceAlias](
    Catalogue,
    BundleEntry(Bundle.Files.PlaceAliases),
    List(rows(ImportRank(33))(PlaceBundleRepo.placeAliasesIn))
  )(scope => PlaceBundleRepo.placeAliases(scope).toList)(
    a => Some(CarriedKind.OwnedClock(a.createdAt.value)),
    (es, ls) => es.modify(_.placeAliases).setTo(ls)
  )

  val siteTypes: CarriedKind = CarriedKind.owned[Bundle.SiteType](
    Catalogue,
    BundleEntry(Bundle.Files.PlaceSiteTypes),
    List(rows(ImportRank(64))(PlaceBundleRepo.siteTypesIn))
  )(scope => PlaceBundleRepo.siteTypes(scope).toList)(
    t => t.checkedAt.map(c => CarriedKind.OwnedClock(c.value)),
    (es, ls) => es.modify(_.siteTypes).setTo(ls)
  )

  /** Every kind of the places. */
  val all: List[CarriedKind] = List(places, placeAliases, siteTypes)

  /** Where the bundle's place ids point here, for the lines of other kinds that name a place: a place the other side
    * renamed whose line is not written keeps its id here.
    */
  def placeHere(w: BundleImport.Writer): BPlaceRow.Id => BPlaceRow.Id = PlaceBundleRepo.placeHere(w)
