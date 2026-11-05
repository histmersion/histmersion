package com.histmersion.util.data.exchange

/** A source of the bundle (`right`) and one here (`left`) that the sources' rules found to be, or perhaps to be, one
  * work: `gate` is the question that decides it when a person must, `byKey` whether a strong key (a DOI, the original
  * file, an ISBN) made them one, `basis` how they were matched as the plan says it (the keys, or the score).
  */
final case class SourcePair(
    right: com.histmersion.api.exchange.Bundle.Source,
    left: com.histmersion.api.exchange.Bundle.Source,
    gate: Option[SourcePair.Gate],
    byKey: SourcePair.ByKey,
    basis: SourcePair.Basis
)

object SourcePair:
  type Gate = Gate.T
  object Gate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByKey = ByKey.T
  object ByKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Basis = Basis.T
  object Basis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
