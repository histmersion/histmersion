package com.histmersion.domain.media.covers

import com.histmersion.api.MediaProvider
import com.histmersion.values.MediaRole

import com.histmersion.db.given
import com.augustnagro.magnum.*

/** The sources a run of covers and illustrations looks at (#1446): those with figures and no illustration yet, those
  * with an ISBN or OCLC number and no cover yet. Every method runs on the connection it is given.
  */
private[covers] object CoverRepo:

  /** Sources with figures, for illustrations: those without one yet (all with `refresh`), or `only`. */
  def illustrationCandidates(only: Option[String], refresh: Boolean, limit: Int)(using DbCon): List[String] =
    sql"""SELECT s.citekey FROM sources s
          WHERE ($only::text IS NULL OR s.citekey = $only::text)
            AND EXISTS (SELECT 1 FROM assets a WHERE a.citekey = s.citekey AND a.mime LIKE 'image/%')
            AND ($refresh OR NOT EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'source'
                  AND m.target_id = s.citekey AND m.role = ${MediaRole.Illustration}))
          ORDER BY s.citekey LIMIT ${limit.max(1)}""".query[String].run().toList

  /** Sources with an ISBN or OCLC number and no cover (all with `refresh`), not asked of Open Library yet (unless
    * `refresh`), or `only`.
    */
  def coverCandidates(only: Option[String], refresh: Boolean, limit: Int)(using DbCon): List[CoverKeys] =
    sql"""SELECT s.citekey,
                 (SELECT min(i.value) FROM source_identifiers i WHERE i.source_uid = s.uid AND i.scheme = 'isbn'),
                 (SELECT min(i.value) FROM source_identifiers i WHERE i.source_uid = s.uid AND i.scheme = 'oclc')
          FROM sources s
          WHERE ($only::text IS NULL OR s.citekey = $only::text)
            AND EXISTS (SELECT 1 FROM source_identifiers i WHERE i.source_uid = s.uid AND i.scheme IN ('isbn', 'oclc'))
            AND ($refresh OR NOT EXISTS (SELECT 1 FROM media_links m WHERE m.target_type = 'source'
                  AND m.target_id = s.citekey AND m.role = ${MediaRole.Cover}))
            AND ($refresh OR $only::text IS NOT NULL OR NOT EXISTS (SELECT 1 FROM media_checks c
                  WHERE c.provider = ${MediaProvider.OpenLibrary} AND c.target_type = 'source' AND c.target_id = s.citekey))
          ORDER BY s.citekey LIMIT ${limit.max(1)}"""
      .query[(String, Option[String], Option[String])]
      .run()
      .toList
      .map(CoverKeys.apply)
