package com.histmersion.domain.catalogue.places

import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference

/** The refined types of the Wikidata sites on the map (those a finding or a picture is placed at): which are still to
  * be asked, and what each was found to be.
  */
trait SiteTypeService:
  /** (place id, Wikidata item, the class the import kept) of the next `limit` sites to refine: never asked, or
    * (`refresh`) asked before `before` (epoch ms), by id.
    */
  def sitesToType(limit: com.histmersion.domain.catalogue.places.SiteTypeService.SitesToTypeLimit, refresh: com.histmersion.domain.catalogue.places.SiteTypeService.SitesToTypeRefresh, before: Option[com.histmersion.domain.catalogue.places.SiteTypeService.SitesToTypeBefore]): List[(com.histmersion.domain.catalogue.places.SiteTypeService.SitesToTypeResultPart1, com.histmersion.domain.catalogue.places.SiteTypeService.SitesToTypeResultPart2, Option[com.histmersion.domain.catalogue.places.SiteTypeService.SitesToTypeResultPart3])]

  def countSitesToType(refresh: com.histmersion.domain.catalogue.places.SiteTypeService.CountSitesToTypeRefresh, before: Option[com.histmersion.domain.catalogue.places.SiteTypeService.CountSitesToTypeBefore]): com.histmersion.domain.catalogue.places.SiteTypeService.CountSitesToTypeResult

  /** Record the refined types (`None` = asked, none of the curated classes), asked now. */
  def setSiteTypes(types: List[(com.histmersion.domain.catalogue.places.SiteTypeService.SetSiteTypesTypesPart1, Option[com.histmersion.domain.catalogue.places.SiteTypeService.SetSiteTypesTypesPart2])]): Unit

object SiteTypeService:
  type SitesToTypeResultPart1 = SitesToTypeResultPart1.T
  object SitesToTypeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SitesToTypeResultPart2 = SitesToTypeResultPart2.T
  object SitesToTypeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SitesToTypeResultPart3 = SitesToTypeResultPart3.T
  object SitesToTypeResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SitesToTypeLimit = SitesToTypeLimit.T
  object SitesToTypeLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SitesToTypeRefresh = SitesToTypeRefresh.T
  object SitesToTypeRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SitesToTypeBefore = SitesToTypeBefore.T
  object SitesToTypeBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountSitesToTypeResult = CountSitesToTypeResult.T
  object CountSitesToTypeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountSitesToTypeRefresh = CountSitesToTypeRefresh.T
  object CountSitesToTypeRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountSitesToTypeBefore = CountSitesToTypeBefore.T
  object CountSitesToTypeBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSiteTypesTypesPart1 = SetSiteTypesTypesPart1.T
  object SetSiteTypesTypesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSiteTypesTypesPart2 = SetSiteTypesTypesPart2.T
  object SetSiteTypesTypesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): SiteTypeService = Implementation(db)

  final private class Implementation(db: Transactor) extends SiteTypeService:
    def sitesToType(limit: com.histmersion.domain.catalogue.places.SiteTypeService.Implementation.SitesToTypeLimit, refresh: com.histmersion.domain.catalogue.places.SiteTypeService.Implementation.SitesToTypeRefresh, before: Option[com.histmersion.domain.catalogue.places.SiteTypeService.Implementation.SitesToTypeBefore]): List[(com.histmersion.domain.catalogue.places.SiteTypeService.Implementation.SitesToTypeResultPart1, com.histmersion.domain.catalogue.places.SiteTypeService.Implementation.SitesToTypeResultPart2, Option[com.histmersion.domain.catalogue.places.SiteTypeService.Implementation.SitesToTypeResultPart3])] =
      db.read(SiteTypeRepo.sitesToType(limit, refresh, before))
    def countSitesToType(refresh: com.histmersion.domain.catalogue.places.SiteTypeService.Implementation.CountSitesToTypeRefresh, before: Option[com.histmersion.domain.catalogue.places.SiteTypeService.Implementation.CountSitesToTypeBefore]): com.histmersion.domain.catalogue.places.SiteTypeService.Implementation.CountSitesToTypeResult =
      db.read(SiteTypeRepo.countSitesToType(refresh, before))
    def setSiteTypes(types: List[(com.histmersion.domain.catalogue.places.SiteTypeService.Implementation.SetSiteTypesTypesPart1, Option[com.histmersion.domain.catalogue.places.SiteTypeService.Implementation.SetSiteTypesTypesPart2])]): Unit = db.transact(SiteTypeRepo.setSiteTypes(types))

  object Implementation:
    type SitesToTypeResultPart1 = SitesToTypeResultPart1.T
    object SitesToTypeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SitesToTypeResultPart2 = SitesToTypeResultPart2.T
    object SitesToTypeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SitesToTypeResultPart3 = SitesToTypeResultPart3.T
    object SitesToTypeResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SitesToTypeLimit = SitesToTypeLimit.T
    object SitesToTypeLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SitesToTypeRefresh = SitesToTypeRefresh.T
    object SitesToTypeRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SitesToTypeBefore = SitesToTypeBefore.T
    object SitesToTypeBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountSitesToTypeResult = CountSitesToTypeResult.T
    object CountSitesToTypeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountSitesToTypeRefresh = CountSitesToTypeRefresh.T
    object CountSitesToTypeRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountSitesToTypeBefore = CountSitesToTypeBefore.T
    object CountSitesToTypeBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSiteTypesTypesPart1 = SetSiteTypesTypesPart1.T
    object SetSiteTypesTypesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSiteTypesTypesPart2 = SetSiteTypesTypesPart2.T
    object SetSiteTypesTypesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The same rules over the shared in-memory state; `now` is the time a site is asked at (epoch ms). */
  final class InMemory(state: AtomicReference[CatalogueState], now: () => Long) extends SiteTypeService:
    private val WikidataSite = "^wikidata_q[1-9][0-9]*$".r

    private def toType(refresh: com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.ToTypeRefresh, before: Option[com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.ToTypeBefore])(s: HeldSite): com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.ToTypeResult =
      val asked = (refresh, before) match
        case (true, Some(ms)) => s.checkedAt.forall(_ < ms)
        case (true, None)     => true
        case _                => s.checkedAt.isEmpty
      WikidataSite.matches(s.id) && s.onMap && asked

    def sitesToType(limit: com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.SitesToTypeLimit, refresh: com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.SitesToTypeRefresh, before: Option[com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.SitesToTypeBefore]): List[(com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.SitesToTypeResultPart1, com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.SitesToTypeResultPart2, Option[com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.SitesToTypeResultPart3])] =
      state.get.sites
        .filter(toType(refresh, before))
        .sortBy(_.id)
        .take(limit.max(0))
        .map(s => (s.id, s.id.drop(9).toUpperCase, s.importClass))
        .toList

    def countSitesToType(refresh: com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.CountSitesToTypeRefresh, before: Option[com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.CountSitesToTypeBefore]): com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.CountSitesToTypeResult =
      state.get.sites.count(toType(refresh, before))

    def setSiteTypes(types: List[(com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.SetSiteTypesTypesPart1, Option[com.histmersion.domain.catalogue.places.SiteTypeService.InMemory.SetSiteTypesTypesPart2])]): Unit =
      val at = now()
      val typed = types.toMap
      val _ = state.updateAndGet(
        _.modify(_.sites)
          .using(
            _.map(s => typed.get(s.id).fold(s)(t => s.modify(_.siteType).setTo(t).modify(_.checkedAt).setTo(Some(at))))
          )
      )

  object InMemory:
    type ToTypeResult = ToTypeResult.T
    object ToTypeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToTypeRefresh = ToTypeRefresh.T
    object ToTypeRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToTypeBefore = ToTypeBefore.T
    object ToTypeBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SitesToTypeResultPart1 = SitesToTypeResultPart1.T
    object SitesToTypeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SitesToTypeResultPart2 = SitesToTypeResultPart2.T
    object SitesToTypeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SitesToTypeResultPart3 = SitesToTypeResultPart3.T
    object SitesToTypeResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SitesToTypeLimit = SitesToTypeLimit.T
    object SitesToTypeLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SitesToTypeRefresh = SitesToTypeRefresh.T
    object SitesToTypeRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SitesToTypeBefore = SitesToTypeBefore.T
    object SitesToTypeBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountSitesToTypeResult = CountSitesToTypeResult.T
    object CountSitesToTypeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountSitesToTypeRefresh = CountSitesToTypeRefresh.T
    object CountSitesToTypeRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountSitesToTypeBefore = CountSitesToTypeBefore.T
    object CountSitesToTypeBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSiteTypesTypesPart1 = SetSiteTypesTypesPart1.T
    object SetSiteTypesTypesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSiteTypesTypesPart2 = SetSiteTypesTypesPart2.T
    object SetSiteTypesTypesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
