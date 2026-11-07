package com.histmersion.util.data.diagnostics

/** An owner's counts and sizes. */
final case class DataFigures(counts: List[DataCount] = Nil):
  /** The count of `noun`, when the owner keeps one. */
  def count(noun: com.histmersion.util.data.diagnostics.DataCount.Noun): Option[com.histmersion.util.data.diagnostics.DataCount.Count] = counts.collectFirst { case DataCount(`noun`, n) => n }

object DataFigures:

  val empty: DataFigures = DataFigures()
