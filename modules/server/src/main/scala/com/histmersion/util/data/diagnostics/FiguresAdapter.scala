package com.histmersion.util.data.diagnostics

import com.histmersion.util.data.DataOwner
import com.augustnagro.magnum.DbCon

/** Today's counts, by owner, until each owner registers its own. */
object FiguresAdapter:
  def of(owner: DataOwner)(using DbCon): DataFigures = DataFigures.empty
