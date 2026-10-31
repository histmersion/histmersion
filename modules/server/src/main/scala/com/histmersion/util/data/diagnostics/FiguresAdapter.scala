package com.histmersion.util.data.diagnostics

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.TablesAdapter.*
import com.augustnagro.magnum.DbCon

/** Today's counts, by owner, until each owner registers its own: what About counts of the corpus, and the jobs by
  * state.
  */
object FiguresAdapter:
  def of(owner: DataOwner)(using DbCon): DataFigures = owner match
    case CatalogueOwner =>
      DataFigures(
        List(
          DataCount("events", StatsRepo.rows("events")),
          DataCount("places", StatsRepo.rows("places")),
          DataCount("periods", StatsRepo.rows("periods")),
          DataCount("presets", StatsRepo.rowsWith("periods", "preset"))
        )
      )
    case LibraryOwner =>
      DataFigures(
        List(DataCount("sources", StatsRepo.rows("sources")), DataCount("findings", StatsRepo.rows("findings")))
      )
    case ResearchOwner => DataFigures(List(DataCount("articles", StatsRepo.rows("articles"))))
    case JobsOwner     => DataFigures(StatsRepo.jobStates.map((state, n) => DataCount(state, n.toLong)))
    case _             => DataFigures.empty
