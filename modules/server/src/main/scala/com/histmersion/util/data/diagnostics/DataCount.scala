package com.histmersion.util.data.diagnostics

/** How many of one kind of record an owner holds. */
final case class DataCount(noun: DataCount.Noun, count: DataCount.Count)

object DataCount:
  type Noun = Noun.T
  object Noun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
