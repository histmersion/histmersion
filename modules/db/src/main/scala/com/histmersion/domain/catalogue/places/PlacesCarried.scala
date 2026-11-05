package com.histmersion.domain.catalogue.places

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
  type RowsRank = RowsRank.T
  object RowsRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceHereResultArg1 = PlaceHereResultArg1.T
  object PlaceHereResultArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceHereResultResult = PlaceHereResultResult.T
  object PlaceHereResultResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Catalogue = DataOwner("catalogue")

  private def rows(rank: com.histmersion.domain.catalogue.places.PlacesCarried.RowsRank)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, ImportRank(rank), write)

  val places: CarriedKind = CarriedKind.records[com.histmersion.api.exchange.Bundle.Place](
    Catalogue,
    Bundle.Kind.Place,
    TableName("places"),
    List(rows(30)(PlaceBundleRepo.placeRenames), rows(31)(PlaceBundleRepo.placesIn))
  )(scope => PlaceBundleRepo.places(scope).toList)(_.updatedAt, (es, ls) => es.modify(_.places).setTo(ls))

  val placeAliases: CarriedKind = CarriedKind.owned[com.histmersion.api.exchange.Bundle.PlaceAlias](
    Catalogue,
    BundleEntry(com.histmersion.api.exchange.Bundle.Files.PlaceAliases),
    List(rows(33)(PlaceBundleRepo.placeAliasesIn))
  )(scope => PlaceBundleRepo.placeAliases(scope).toList)(
    a => Some(a.createdAt),
    (es, ls) => es.modify(_.placeAliases).setTo(ls)
  )

  val siteTypes: CarriedKind = CarriedKind.owned[com.histmersion.api.exchange.Bundle.SiteType](
    Catalogue,
    BundleEntry(com.histmersion.api.exchange.Bundle.Files.PlaceSiteTypes),
    List(rows(64)(PlaceBundleRepo.siteTypesIn))
  )(scope => PlaceBundleRepo.siteTypes(scope).toList)(_.checkedAt, (es, ls) => es.modify(_.siteTypes).setTo(ls))

  /** Every kind of the places. */
  val all: List[CarriedKind] = List(places, placeAliases, siteTypes)

  /** Where the bundle's place ids point here, for the lines of other kinds that name a place: a place the other side
    * renamed whose line is not written keeps its id here.
    */
  def placeHere(w: BundleImport.Writer): com.histmersion.domain.catalogue.places.PlacesCarried.PlaceHereResultArg1 => com.histmersion.domain.catalogue.places.PlacesCarried.PlaceHereResultResult = PlaceBundleRepo.placeHere(w)
