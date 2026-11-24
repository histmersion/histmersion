package com.histmersion
package domain.catalogue.places

import com.histmersion.api.library.{DeleteCount, DeletePart}
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.augustnagro.magnum.*
import com.histmersion.api.library.Rename
import com.histmersion.domain.{CreatedAt, Creator}

/** The gazetteer's provenance that names a source (a person's place types and footprints, a place's `source`), which a
  * delete keeps as history, and the places its findings were assigned to, which go with them.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "counts and removes the place rows that point at a deleted source")
final class PlaceSourceReferrer extends SourceReferrer:

  override def renamed(rename: SourceRename)(using DbCon)(using Creator.New, CreatedAt): RenamedReferences =
    val (from, to) = (rename.from, rename.to)
    RenamedReferences(
      List(
        "place_types.citekey" -> sql"UPDATE place_types SET citekey = $to WHERE citekey = $from".update.run(),
        "place_footprints.citekey" -> sql"UPDATE place_footprints SET citekey = $to WHERE citekey = $from".update.run(),
        "places.source" -> sql"UPDATE places SET source = $to WHERE source = $from".update.run()
      ).collect { case (t, n) if n > 0 => Rename.Count(Rename.Count.Table(t), Rename.Count.Rows(n)) }
    )

  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val placed = sql"""SELECT count(*)::int FROM finding_places fp JOIN findings f ON f.id = fp.finding_id
                       WHERE f.citekey = ${source.citekey}""".query[Int].run().headOption.getOrElse(0)
    DeleteReport(counts = List(DeletePart.FindingPlaces -> placed).collect {
      case (p, rows) if rows > 0 => DeleteCount(p, DeleteCount.Rows(rows))
    })
