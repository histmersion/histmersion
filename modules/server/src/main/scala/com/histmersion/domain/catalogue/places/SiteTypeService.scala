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
  def sitesToType(limit: Int, refresh: Boolean, before: Option[Long]): List[(String, String, Option[String])]

  def countSitesToType(refresh: Boolean, before: Option[Long]): Int

  /** Record the refined types (`None` = asked, none of the curated classes), asked now. */
  def setSiteTypes(types: List[(String, Option[String])]): Unit

object SiteTypeService:
  def apply(db: Transactor): SiteTypeService = Implementation(db)

  final private class Implementation(db: Transactor) extends SiteTypeService:
    def sitesToType(limit: Int, refresh: Boolean, before: Option[Long]): List[(String, String, Option[String])] =
      db.read(SiteTypeRepo.sitesToType(limit, refresh, before))
    def countSitesToType(refresh: Boolean, before: Option[Long]): Int =
      db.read(SiteTypeRepo.countSitesToType(refresh, before))
    def setSiteTypes(types: List[(String, Option[String])]): Unit = db.transact(SiteTypeRepo.setSiteTypes(types))

  /** The same rules over the shared in-memory state; `now` is the time a site is asked at (epoch ms). */
  final class InMemory(state: AtomicReference[CatalogueState], now: () => Long) extends SiteTypeService:
    private val WikidataSite = "^wikidata_q[1-9][0-9]*$".r

    private def toType(refresh: Boolean, before: Option[Long])(s: HeldSite): Boolean =
      val asked = (refresh, before) match
        case (true, Some(ms)) => s.checkedAt.forall(_ < ms)
        case (true, None)     => true
        case _                => s.checkedAt.isEmpty
      WikidataSite.matches(s.id) && s.onMap && asked

    def sitesToType(limit: Int, refresh: Boolean, before: Option[Long]): List[(String, String, Option[String])] =
      state.get.sites
        .filter(toType(refresh, before))
        .sortBy(_.id)
        .take(limit.max(0))
        .map(s => (s.id, s.id.drop(9).toUpperCase, s.importClass))
        .toList

    def countSitesToType(refresh: Boolean, before: Option[Long]): Int =
      state.get.sites.count(toType(refresh, before))

    def setSiteTypes(types: List[(String, Option[String])]): Unit =
      val at = now()
      val typed = types.toMap
      val _ = state.updateAndGet(
        _.modify(_.sites)
          .using(_.map(s =>
            typed.get(s.id).fold(s)(t => s.modify(_.siteType).setTo(t).modify(_.checkedAt).setTo(Some(at)))
          ))
      )
