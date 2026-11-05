package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.{Param, Sql}

import com.augustnagro.magnum.*
import com.histmersion.util.database.given_DbCodec_List

/** The refined site types of the Wikidata sites (`places.site_type`, `site_type_checked_at`). Every method runs on the
  * connection it is given.
  */
private[places] object SiteTypeRepo:

  /** The Wikidata sites on the map (with a finding or a picture) whose type is still to be refined: never asked, or
    * (`refresh`) asked before `before` (epoch ms — the start of this run).
    */
  type ToTypeWhereRefresh = ToTypeWhereRefresh.T
  object ToTypeWhereRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToTypeWhereBefore = ToTypeWhereBefore.T
  object ToTypeWhereBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
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

  private def toTypeWhere(refresh: com.histmersion.domain.catalogue.places.SiteTypeRepo.ToTypeWhereRefresh, before: Option[com.histmersion.domain.catalogue.places.SiteTypeRepo.ToTypeWhereBefore]): Sql =
    val asked = (refresh, before) match
      case (true, Some(ms)) =>
        Sql.of("(p.site_type_checked_at IS NULL OR p.site_type_checked_at < to_timestamp(? / 1000.0))", Param.L(ms))
      case (true, None) => Sql.raw("TRUE")
      case _            => Sql.raw("p.site_type_checked_at IS NULL")
    Sql.raw("p.import_source = 'wikidata' AND p.id ~ '^wikidata_q[1-9][0-9]*$' AND ") ++ asked ++
      Sql.raw(
        " AND (EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id)" +
          " OR EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'place' AND m.target_id = p.id))"
      )

  /** (place id, Wikidata item, the class the import kept) of the next `limit` sites to refine. */
  def sitesToType(
      limit: com.histmersion.domain.catalogue.places.SiteTypeRepo.SitesToTypeLimit,
      refresh: com.histmersion.domain.catalogue.places.SiteTypeRepo.SitesToTypeRefresh,
      before: Option[com.histmersion.domain.catalogue.places.SiteTypeRepo.SitesToTypeBefore]
  )(using DbCon): List[(com.histmersion.domain.catalogue.places.SiteTypeRepo.SitesToTypeResultPart1, com.histmersion.domain.catalogue.places.SiteTypeRepo.SitesToTypeResultPart2, Option[com.histmersion.domain.catalogue.places.SiteTypeRepo.SitesToTypeResultPart3])] =
    (Sql.raw("SELECT p.id, upper(substr(p.id, 10)), p.properties->>'class' FROM places p WHERE ") ++
      toTypeWhere(refresh, before) ++ Sql.of(" ORDER BY p.id LIMIT ?", Param.I(limit.max(0)))).frag
      .query[(String, String, Option[String])]
      .run()
      .toList

  def countSitesToType(refresh: com.histmersion.domain.catalogue.places.SiteTypeRepo.CountSitesToTypeRefresh, before: Option[com.histmersion.domain.catalogue.places.SiteTypeRepo.CountSitesToTypeBefore])(using DbCon): com.histmersion.domain.catalogue.places.SiteTypeRepo.CountSitesToTypeResult =
    (Sql.raw("SELECT count(*)::int FROM places p WHERE ") ++ toTypeWhere(refresh, before)).frag.query[Int].run().head

  /** Record the refined types (`None` = asked, none of the curated classes). */
  def setSiteTypes(types: List[(com.histmersion.domain.catalogue.places.SiteTypeRepo.SetSiteTypesTypesPart1, Option[com.histmersion.domain.catalogue.places.SiteTypeRepo.SetSiteTypesTypesPart2])])(using DbCon): Unit =
    val _ =
      batchUpdate(types) { (id, t) =>
        sql"UPDATE places SET site_type = $t, site_type_checked_at = now() WHERE id = $id".update
      }
