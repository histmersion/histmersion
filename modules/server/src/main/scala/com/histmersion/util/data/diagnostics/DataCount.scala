package com.histmersion
package util.data.diagnostics

import io.github.iltotore.iron.constraint.any.Pure

/** How many of one kind of record an owner holds. */
final case class DataCount(noun: DataCount.Noun, count: DataCount.Count)

object DataCount:
  type Noun = Noun.T
  object Noun extends Newtype[String, Pure]
  type Count = Count.T
  object Count extends Newtype[Long, Pure]
