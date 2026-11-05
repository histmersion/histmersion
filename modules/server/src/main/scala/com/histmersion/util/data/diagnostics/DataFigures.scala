package com.histmersion.util.data.diagnostics

/** An owner's counts and sizes. */
final case class DataFigures(counts: List[DataCount] = Nil):
  /** The count of `noun`, when the owner keeps one. */
  def count(noun: com.histmersion.util.data.diagnostics.DataFigures.CountNoun): Option[com.histmersion.util.data.diagnostics.DataFigures.CountResult] = counts.collectFirst { case DataCount(`noun`, n) => n }

object DataFigures:
  type CountResult = CountResult.T
  object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountNoun = CountNoun.T
  object CountNoun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val empty: DataFigures = DataFigures()
