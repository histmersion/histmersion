package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.{Param, Sql}

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.given_DbCodec_List

/** The refined site types of the Wikidata sites (`places.site_type`, `site_type_checked_at`). Every method runs on the
  * connection it is given.
  */
private[places] object SiteTypeRepo:
  type Refresh = Refresh.T
  object Refresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The Wikidata sites on the map (with a finding or a picture) whose type is still to be refined: never asked, or
    * (`refresh`) asked before `before` (epoch ms — the start of this run).
    */

  private def toTypeWhere(refresh: com.histmersion.domain.catalogue.places.SiteTypeRepo.Refresh, before: Option[com.histmersion.domain.DeepTime.SignedYear]): Sql =
    val asked = (refresh.value, before) match
      case (true, Some(ms)) =>
        Sql.of(
          Sql.OfText("(p.site_type_checked_at IS NULL OR p.site_type_checked_at < to_timestamp(? / 1000.0))"),
          Param.L(Param.L.V(ms.value))
        )
      case (true, None) => Sql.raw(com.histmersion.util.database.Sql.RawText("TRUE"))
      case _            => Sql.raw(com.histmersion.util.database.Sql.RawText("p.site_type_checked_at IS NULL"))
    Sql.raw(Sql.Text("p.import_source = 'wikidata' AND p.id ~ '^wikidata_q[1-9][0-9]*$' AND ")) ++ asked ++
      Sql.raw(
        Sql.Text(
          " AND (EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id)" +
            " OR EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'place' AND m.target_id = p.id))"
        )
      )

  /** (place id, Wikidata item, the class the import kept) of the next `limit` sites to refine. */
  def sitesToType(
      limit: com.histmersion.domain.catalogue.places.PlaceRepo.Limit,
      refresh: com.histmersion.domain.catalogue.places.SiteTypeRepo.Refresh,
      before: Option[com.histmersion.domain.DeepTime.SignedYear]
  )(using DbCon): List[(com.histmersion.domain.catalogue.Place.Id, com.histmersion.domain.catalogue.places.PlaceRepo.Label, Option[com.histmersion.domain.catalogue.places.PlaceRepo.SiteType])] =
    (Sql.raw(Sql.Text("SELECT p.id, upper(substr(p.id, 10)), p.properties->>'class' FROM places p WHERE ")) ++
      toTypeWhere(refresh, before) ++ Sql.of(Sql.OfText(" ORDER BY p.id LIMIT ?"), Param.I(Param.I.V(limit.value.max(0))))).frag
      .query[(com.histmersion.domain.catalogue.Place.Id, com.histmersion.domain.catalogue.places.PlaceRepo.Label, Option[com.histmersion.domain.catalogue.places.PlaceRepo.SiteType])]
      .run()
      .toList

  def countSitesToType(refresh: com.histmersion.domain.catalogue.places.SiteTypeRepo.Refresh, before: Option[com.histmersion.domain.DeepTime.SignedYear])(using DbCon): com.histmersion.RowCount =
    com.histmersion.RowCount(
      (Sql.raw(com.histmersion.util.database.Sql.RawText("SELECT count(*)::int FROM places p WHERE ")) ++ toTypeWhere(refresh, before)).frag.query[Int].run().head
    )

  /** Record the refined types (`None` = asked, none of the curated classes). */
  def setSiteTypes(types: List[(com.histmersion.domain.catalogue.Place.Id, Option[com.histmersion.domain.catalogue.places.PlaceRepo.SiteType])])(using DbCon): Unit =
    val _ =
      batchUpdate(types) { (id, t) =>
        sql"UPDATE places SET site_type = $t, site_type_checked_at = now() WHERE id = $id".update
      }
