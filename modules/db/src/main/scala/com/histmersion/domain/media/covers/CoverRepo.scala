package com.histmersion
package domain.media.covers

import com.histmersion.api.media.MediaProvider
import com.histmersion.domain.media.MediaRole

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** The sources a run of covers and illustrations looks at (#1446): those with figures and no illustration yet, those
  * with an ISBN or OCLC number and no cover yet. Every method runs on the connection it is given.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the cover tables and their row codecs")
private[covers] object CoverRepo:
  type Only = Only.T
  object Only extends Newtype[String, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  enum Refresh derives CanEqual:
    case Refresh, Reuse
  object Refresh extends TwoCases[Refresh]:
    extension (refresh: Refresh) def refresh: Boolean = refresh == Refresh

  /** Sources with figures, for illustrations: those without one yet (all with `refresh`), or `only`. */

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def illustrationCandidates(only: Option[Only], refresh: Refresh, limit: Limit)(using DbCon): List[String] =
    sql"""SELECT s.citekey FROM sources s
          WHERE ($only::text IS NULL OR s.citekey = $only::text)
            AND EXISTS (SELECT 1 FROM assets a WHERE a.citekey = s.citekey AND a.mime LIKE 'image/%')
            AND (${refresh.refresh} OR NOT EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'source'
                  AND m.target_id = s.citekey AND m.role = ${MediaRole.Illustration}))
          ORDER BY s.citekey LIMIT ${limit.value.max(1)}""".query[String].run().toList

  /** Sources with an ISBN or OCLC number and no cover (all with `refresh`), not asked of Open Library yet (unless
    * `refresh`), or `only`.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def coverCandidates(only: Option[Only], refresh: Refresh, limit: Limit)(using DbCon): List[CoverKeys] =
    sql"""SELECT s.citekey,
                 (SELECT min(i.value) FROM source_identifiers i WHERE i.source_uid = s.uid AND i.scheme = 'isbn'),
                 (SELECT min(i.value) FROM source_identifiers i WHERE i.source_uid = s.uid AND i.scheme = 'oclc')
          FROM sources s
          WHERE ($only::text IS NULL OR s.citekey = $only::text)
            AND EXISTS (SELECT 1 FROM source_identifiers i WHERE i.source_uid = s.uid AND i.scheme IN ('isbn', 'oclc'))
            AND (${refresh.refresh} OR NOT EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'source'
                  AND m.target_id = s.citekey AND m.role = ${MediaRole.Cover}))
            AND (${refresh.refresh} OR $only::text IS NOT NULL OR NOT EXISTS (SELECT 1 FROM media_checks c
                  WHERE c.provider = ${MediaProvider.OpenLibrary} AND c.target_type = 'source' AND c.target_id = s.citekey))
          ORDER BY s.citekey LIMIT ${limit.value.max(1)}"""
      .query[(Citekey, Option[CoverKeys.Isbn], Option[CoverKeys.Oclc])]
      .run()
      .toList
      .map(CoverKeys.apply)
