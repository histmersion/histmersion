package com.histmersion.util.data.diagnostics

/** An owner's counts and sizes. */
final case class DataFigures(counts: List[DataCount] = Nil)

object DataFigures:
  val empty: DataFigures = DataFigures()
