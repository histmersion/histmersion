package com.histmersion.domain.media.figures

import com.histmersion.api.{FigureLicenceFill, MediaCount}
import com.histmersion.db.Sql

import com.histmersion.db.given
import com.augustnagro.magnum.*

/** A source's figures take the source's licence (#1446): the figures with no licence whose source records one. Runs on
  * the connection it is given.
  */
private[histmersion] object FigureRepo:

  private val figureCandidates =
    """FROM assets a JOIN sources s ON s.citekey = a.citekey
       WHERE a.provider IS NULL AND (a.license IS NULL OR btrim(a.license) = '')
         AND s.license IS NOT NULL AND btrim(s.license) <> ''"""

  /** Count (and with `apply`, write) the figures with no licence whose source records one. */
  def fillLicences(apply: Boolean)(using DbCon): FigureLicenceFill =
    val by = Sql
      .raw(s"SELECT a.citekey, count(*)::int $figureCandidates GROUP BY a.citekey ORDER BY 2 DESC, 1")
      .frag
      .query[(String, Int)]
      .run()
      .toList
      .map(MediaCount(_, _))
    val candidates = by.map(_.n).sum
    val updated =
      if !apply then 0
      else sql"""UPDATE assets a SET license = btrim(s.license) FROM sources s
            WHERE s.citekey = a.citekey AND a.provider IS NULL AND (a.license IS NULL OR btrim(a.license) = '')
              AND s.license IS NOT NULL AND btrim(s.license) <> ''""".update.run()
    FigureLicenceFill(candidates, updated, apply, by)
