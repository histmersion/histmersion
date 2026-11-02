package com.histmersion.util.data.diagnostics

import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.TablesAdapter.*
import com.augustnagro.magnum.DbCon

/** Today's counts, by owner, until each owner registers its own: what About counts of the corpus, and the jobs by
  * state.
  */
object FiguresAdapter:
  def of(owner: DataOwner)(using DbCon): DataFigures = owner match
    case JobsOwner => DataFigures(StatsRepo.jobStates.map((state, n) => DataCount(state, n.toLong)))
    case _         => DataFigures.empty
