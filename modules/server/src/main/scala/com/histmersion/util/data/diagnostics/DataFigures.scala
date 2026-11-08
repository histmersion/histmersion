package com.histmersion
package util.data.diagnostics

/** An owner's counts and sizes. */
final case class DataFigures(counts: List[DataCount] = Nil):
  /** The count of `noun`, when the owner keeps one. */
  def count(noun: DataCount.Noun): Option[DataCount.Count] = counts.collectFirst { case DataCount(`noun`, n) => n }

object DataFigures:

  val empty: DataFigures = DataFigures()
