package com.histmersion
package domain.catalogue.places

import com.histmersion.api.catalogue.{CatalogRecord, SiteTypes}
import com.histmersion.domain.DeepTime
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.domain.catalogue.Place as DPlace
import com.histmersion.upstreams.wikidata.WikidataItem
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import io.github.iltotore.iron.constraint.any.Pure

/** The refined types of the Wikidata sites on the map (those a finding or a picture is placed at): which are still to
  * be asked, and what each was found to be.
  */
trait SiteTypeService:
  /** (place id, Wikidata item, the class the import kept) of the next `limit` sites to refine: never asked, or
    * (`refresh`) asked before `before` (epoch ms), by id.
    */
  def sitesToType(limit: PageSize, refresh: SiteTypeService.Refresh, before: Option[DeepTime.SignedYear]): List[(DPlace.Id, WikidataItem.Id, Option[CatalogRecord.Wikidata.ImportClass])]

  def countSitesToType(refresh: SiteTypeService.Refresh, before: Option[DeepTime.SignedYear]): RowCount

  /** Record the refined types (`None` = asked, none of the curated classes), asked now. */
  def setSiteTypes(types: List[(DPlace.Id, Option[SiteTypes.SiteType.Id])]): Unit

object SiteTypeService:
  type Refresh = Refresh.T
  object Refresh extends Newtype[Boolean, Pure]

  def apply(db: Transactor): SiteTypeService = Implementation(db)

  final private class Implementation(db: Transactor) extends SiteTypeService:
    def sitesToType(limit: PageSize, refresh: Refresh, before: Option[DeepTime.SignedYear]): List[(DPlace.Id, WikidataItem.Id, Option[CatalogRecord.Wikidata.ImportClass])] =
      db.read(SiteTypeRepo.sitesToType(PlaceRepo.Limit(limit.value), SiteTypeRepo.Refresh(refresh.value), before))
        .map((id, item, cls) => (id, WikidataItem.Id(item.value), cls.map(c => CatalogRecord.Wikidata.ImportClass(c.value))))
    def countSitesToType(refresh: Refresh, before: Option[DeepTime.SignedYear]): RowCount =
      db.read(SiteTypeRepo.countSitesToType(SiteTypeRepo.Refresh(refresh.value), before))
    def setSiteTypes(types: List[(DPlace.Id, Option[SiteTypes.SiteType.Id])]): Unit =
      db.transact(SiteTypeRepo.setSiteTypes(types.map((id, t) => id -> t.map(x => PlaceRepo.SiteType(x.value)))))


  /** The same rules over the shared in-memory state; `now` is the time a site is asked at (epoch ms). */
  final class InMemory(state: AtomicReference[CatalogueState], now: () => HeldSite.CheckedAt) extends SiteTypeService:
    private val WikidataSite = "^wikidata_q[1-9][0-9]*$".r

    private def toType(refresh: Refresh, before: Option[DeepTime.SignedYear])(s: HeldSite): Boolean =
      val asked = (refresh.value, before) match
        case (true, Some(ms)) => s.checkedAt.forall(_.value < ms.value)
        case (true, None)     => true
        case _                => s.checkedAt.isEmpty
      WikidataSite.matches(s.id.value) && s.onMap.value && asked

    def sitesToType(limit: PageSize, refresh: Refresh, before: Option[DeepTime.SignedYear]): List[(DPlace.Id, WikidataItem.Id, Option[CatalogRecord.Wikidata.ImportClass])] =
      state.get.sites
        .filter(toType(refresh, before))
        .sortBy(_.id.value)
        .take(limit.value)
        .flatMap(s =>
          DPlace.Id.option(s.id.value).map(id =>
            (id, WikidataItem.Id(s.id.value.drop(9).toUpperCase), s.importClass.map(c => CatalogRecord.Wikidata.ImportClass(c.value)))
          )
        )
        .toList

    def countSitesToType(refresh: Refresh, before: Option[DeepTime.SignedYear]): RowCount =
      RowCount(state.get.sites.count(toType(refresh, before)))

    def setSiteTypes(types: List[(DPlace.Id, Option[SiteTypes.SiteType.Id])]): Unit =
      val at = now()
      val typed = types.map((id, t) => HeldSite.Id(id.value) -> t).toMap
      val _ = state.updateAndGet(
        _.modify(_.sites)
          .using(
            _.map(s =>
              typed.get(s.id).fold(s)(t =>
                s.modify(_.siteType).setTo(t.map(x => HeldSite.SiteType(x.value))).modify(_.checkedAt).setTo(Some(at))
              )
            )
          )
      )
