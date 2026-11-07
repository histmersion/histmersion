package com.histmersion.domain.media.figures

import com.histmersion.api.media.{FigureLicenceFill, MediaCount}
import com.histmersion.util.database.Sql

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given

/** A source's figures take the source's licence (#1446): the figures with no licence whose source records one. Runs on
  * the connection it is given.
  */
private[figures] object FigureRepo:
  private val figureCandidates =
    """FROM assets a JOIN sources s ON s.citekey = a.citekey
       WHERE a.provider IS NULL AND (a.license IS NULL OR btrim(a.license) = '')
         AND s.license IS NOT NULL AND btrim(s.license) <> ''"""

  /** Count (and with `apply`, write) the figures with no licence whose source records one. */
  def fillLicences(apply: FigureLicenceFill.Applied)(using DbCon): FigureLicenceFill =
    val by = Sql
      .raw(Sql.RawText(s"SELECT a.citekey, count(*)::int $figureCandidates GROUP BY a.citekey ORDER BY 2 DESC, 1"))
      .frag
      .query[(MediaCount.Key, MediaCount.N)]
      .run()
      .toList
      .map(MediaCount(_, _))
    val candidates = FigureLicenceFill.Candidates(by.map(_.n.value).sum)
    val updated = FigureLicenceFill.Updated(
      if !apply.value then 0
      else sql"""UPDATE assets a SET license = btrim(s.license) FROM sources s
            WHERE s.citekey = a.citekey AND a.provider IS NULL AND (a.license IS NULL OR btrim(a.license) = '')
              AND s.license IS NOT NULL AND btrim(s.license) <> ''""".update.run()
    )
    FigureLicenceFill(candidates, updated, apply, by)
