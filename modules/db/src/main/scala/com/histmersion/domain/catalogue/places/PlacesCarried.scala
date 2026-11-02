package com.histmersion.domain.catalogue.places

import com.histmersion.api.{BundleFiles, BundlePlace, BundlePlaceAlias, BundleSiteType}
import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{BundleEntry, CarriedKind, ImportRank, ImportStage, RowsIn}
import com.histmersion.values.BundleKind

import hearth.kindlings.optics.syntax.*

/** What a bundle carries of the places: this collection's own places (renamed first, as the other side renamed them),
  * their old ids, and the site types refined from outside.
  */
object PlacesCarried:
  private val Catalogue = DataOwner("catalogue")

  private def rows(rank: Int)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, ImportRank(rank), write)

  val places: CarriedKind = CarriedKind.records[BundlePlace](
    Catalogue,
    BundleKind.Place,
    TableName("places"),
    List(rows(30)(PlaceBundleRepo.placeRenames), rows(31)(PlaceBundleRepo.placesIn))
  )(scope => PlaceBundleRepo.places(scope).toList)(_.updatedAt, (es, ls) => es.modify(_.places).setTo(ls))

  val placeAliases: CarriedKind = CarriedKind.owned[BundlePlaceAlias](
    Catalogue,
    BundleEntry(BundleFiles.PlaceAliases),
    List(rows(33)(PlaceBundleRepo.placeAliasesIn))
  )(scope => PlaceBundleRepo.placeAliases(scope).toList)(
    a => Some(a.createdAt),
    (es, ls) => es.modify(_.placeAliases).setTo(ls)
  )

  val siteTypes: CarriedKind = CarriedKind.owned[BundleSiteType](
    Catalogue,
    BundleEntry(BundleFiles.PlaceSiteTypes),
    List(rows(64)(PlaceBundleRepo.siteTypesIn))
  )(scope => PlaceBundleRepo.siteTypes(scope).toList)(_.checkedAt, (es, ls) => es.modify(_.siteTypes).setTo(ls))

  /** Every kind of the places. */
  val all: List[CarriedKind] = List(places, placeAliases, siteTypes)

  /** Where the bundle's place ids point here, for the lines of other kinds that name a place: a place the other side
    * renamed whose line is not written keeps its id here.
    */
  def placeHere(w: BundleImport.Writer): String => String = PlaceBundleRepo.placeHere(w)
