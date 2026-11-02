package com.histmersion.domain.catalogue.places

import com.histmersion.api.{DeleteCount, DeletePart, RenameCount}
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given

import com.augustnagro.magnum.*

/** The gazetteer's provenance that names a source (a person's place types and footprints, a place's `source`), which a
  * delete keeps as history, and the places its findings were assigned to, which go with them.
  */
final class PlaceSourceReferrer extends SourceReferrer:

  override def renamed(rename: SourceRename)(using DbCon): RenamedReferences =
    val (from, to) = (rename.from, rename.to)
    RenamedReferences(
      List(
        "place_types.citekey" -> sql"UPDATE place_types SET citekey = $to WHERE citekey = $from".update.run(),
        "place_footprints.citekey" -> sql"UPDATE place_footprints SET citekey = $to WHERE citekey = $from".update.run(),
        "places.source" -> sql"UPDATE places SET source = $to WHERE source = $from".update.run()
      ).collect { case (t, n) if n > 0 => RenameCount(t, n) }
    )

  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val placed = sql"""SELECT count(*)::int FROM finding_places fp JOIN findings f ON f.id = fp.finding_id
                       WHERE f.citekey = ${source.citekey}""".query[Int].run().headOption.getOrElse(0)
    DeleteReport(counts = List(DeletePart.FindingPlaces -> placed).collect {
      case (p, rows) if rows > 0 => DeleteCount(p, rows)
    })
