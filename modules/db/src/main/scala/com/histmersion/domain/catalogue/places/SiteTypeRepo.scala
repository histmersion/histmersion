package com.histmersion
package domain.catalogue.places

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.given_DbCodec_List
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.DeepTime
import com.histmersion.domain.catalogue.Place
import com.histmersion.util.database.Unused
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** The refined site types of the Wikidata sites (`places.site_type`, `site_type_checked_at`). Every method runs on the
  * connection it is given.
  */
@atBoundary(BoundaryKind.Sql, "reads and writes the site type tables and their row codecs")
private[places] object SiteTypeRepo:
  type Refresh = Refresh.T
  object Refresh extends Newtype[Boolean, Pure]

  /** The Wikidata sites on the map (with a finding or a picture) whose type is still to be refined: never asked, or
    * (`refresh`) asked before `before` (epoch ms — the start of this run).
    */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def toTypeWhere(refresh: Refresh, before: Option[DeepTime.SignedYear]): Frag =
    val asked = (refresh.value, before) match
      case (true, Some(ms)) =>
        sql"(p.site_type_checked_at IS NULL OR p.site_type_checked_at < to_timestamp($ms / 1000.0))"
      case (true, None) => sql"TRUE"
      case _            => sql"p.site_type_checked_at IS NULL"
    sql"p.import_source = 'wikidata' AND p.id ~ '^wikidata_q[1-9][0-9]*$$' AND $asked AND (EXISTS (SELECT 1 FROM finding_places fp WHERE fp.place_id = p.id) OR EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'place' AND m.target_id = p.id))"

  /** (place id, Wikidata item, the class the import kept) of the next `limit` sites to refine. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def sitesToType(
      limit: PlaceRepo.Limit,
      refresh: Refresh,
      before: Option[DeepTime.SignedYear]
  )(using DbCon): List[(Place.Id, PlaceRepo.Label, Option[PlaceRepo.SiteType])] =
    sql"SELECT p.id, upper(substr(p.id, 10)), p.properties->>'class' FROM places p WHERE ${toTypeWhere(refresh, before)} ORDER BY p.id LIMIT ${limit.value.max(0)}"
      .query[(Place.Id, PlaceRepo.Label, Option[PlaceRepo.SiteType])]
      .run()
      .toList

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def countSitesToType(refresh: Refresh, before: Option[DeepTime.SignedYear])(using DbCon): RowCount =
    RowCount(
      sql"SELECT count(*)::int FROM places p WHERE ${toTypeWhere(refresh, before)}"
        .query[Int]
        .run()
        .head
    )

  /** Record the refined types (`None` = asked, none of the curated classes). */
  def setSiteTypes(types: List[(Place.Id, Option[PlaceRepo.SiteType])])(using DbCon): Unit =
    Unused(
      batchUpdate(types) { (id, t) =>
        sql"UPDATE places SET site_type = $t, site_type_checked_at = now() WHERE id = $id".update
      },
      Unused.Why.Idempotent
    )
