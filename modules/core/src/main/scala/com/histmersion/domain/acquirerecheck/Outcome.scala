package com.histmersion
package domain.acquirerecheck

import hearth.kindlings.optics.syntax.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library
import com.histmersion.Newtype

enum Outcome(val wire: Outcome.Wire) derives CanEqual:
  case Closed extends Outcome(Outcome.Wire("closed"))

  case Fetched extends Outcome(Outcome.Wire("fetched"))

  case NoCopy extends Outcome(Outcome.Wire("no-copy"))

  case Failed extends Outcome(Outcome.Wire("failed"))

object Outcome:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Outcome] = values.find(_.wire.value == s.value.trim)

  def of(o: library.acquisition.AcquireAttempt.Outcome): Option[Outcome] = o match
    case library.acquisition.AcquireAttempt.Outcome.Closed(_)    => Some(Closed)
    case library.acquisition.AcquireAttempt.Outcome.Fetched(_)   => Some(Fetched)
    case library.acquisition.AcquireAttempt.Outcome.NoCopy       => Some(NoCopy)
    case library.acquisition.AcquireAttempt.Outcome.Failed(_)    => Some(Failed)
    case library.acquisition.AcquireAttempt.Outcome.Legacy(_, _) => None
