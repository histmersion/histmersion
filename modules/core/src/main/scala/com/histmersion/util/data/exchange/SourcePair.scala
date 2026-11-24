package com.histmersion
package util.data.exchange

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** A source of the bundle (`right`) and one here (`left`) that the sources' rules found to be, or perhaps to be, one
  * work: `gate` is the question that decides it when a person must, `byKey` whether a strong key (a DOI, the original
  * file, an ISBN) made them one, `basis` how they were matched as the plan says it (the keys, or the score).
  */
@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
final case class SourcePair(
    right: com.histmersion.api.exchange.Bundle.Source,
    left: com.histmersion.api.exchange.Bundle.Source,
    gate: Option[SourcePair.Gate],
    byKey: SourcePair.ByKey,
    basis: SourcePair.Basis
)

object SourcePair:
  type Gate = Gate.T
  object Gate extends Newtype[String, Pure]
  type ByKey = ByKey.T
  object ByKey extends Newtype[Boolean, Pure]
  type Basis = Basis.T
  object Basis extends Newtype[String, Pure]
