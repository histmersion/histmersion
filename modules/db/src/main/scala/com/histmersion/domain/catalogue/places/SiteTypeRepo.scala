package com.histmersion.domain.catalogue.places

import com.histmersion.db.{Param, Sql}

import com.augustnagro.magnum.*
import com.histmersion.db.given_DbCodec_List

/** The refined site types of the Wikidata sites (`places.site_type`, `site_type_checked_at`). Every method runs on the
  * connection it is given.
  */
private[places] object SiteTypeRepo:

  /** The Wikidata sites on the map (with a finding or a picture) whose type is still to be refined: never asked, or
    * (`refresh`) asked before `before` (epoch ms — the start of this run).
    */
  private def toTypeWhere(refresh: Boolean, before: Option[Long]): Sql =
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
      limit: Int,
      refresh: Boolean,
      before: Option[Long]
  )(using DbCon): List[(String, String, Option[String])] =
      (Sql.raw("SELECT p.id, upper(substr(p.id, 10)), p.properties->>'class' FROM places p WHERE ") ++
        toTypeWhere(refresh, before) ++ Sql.of(" ORDER BY p.id LIMIT ?", Param.I(limit.max(0)))).frag
        .query[(String, String, Option[String])]
        .run()
        .toList

  def countSitesToType(refresh: Boolean, before: Option[Long])(using DbCon): Int =
      (Sql.raw("SELECT count(*)::int FROM places p WHERE ") ++ toTypeWhere(refresh, before)).frag.query[Int].run().head

  /** Record the refined types (`None` = asked, none of the curated classes). */
  def setSiteTypes(types: List[(String, Option[String])])(using DbCon): Unit =
    val _ =
      batchUpdate(types) { (id, t) =>
        sql"UPDATE places SET site_type = $t, site_type_checked_at = now() WHERE id = $id".update
      }
