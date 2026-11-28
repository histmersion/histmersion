package com.histmersion
package domain.acquirerecheck

import hearth.kindlings.optics.syntax.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library
import com.histmersion.Newtype

enum Upshot(val wire: Upshot.Wire) derives CanEqual:
  case Closed extends Upshot(Upshot.Wire("closed"))

  case Fetched extends Upshot(Upshot.Wire("fetched"))

  case NoCopy extends Upshot(Upshot.Wire("no-copy"))

  case Failed extends Upshot(Upshot.Wire("failed"))

object Upshot:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Upshot] = values.find(_.wire.value == s.value.trim)

  def of(o: library.acquisition.AcquireAttempt.Outcome): Option[Upshot] = o match
    case library.acquisition.AcquireAttempt.Outcome.Closed(_)    => Some(Closed)
    case library.acquisition.AcquireAttempt.Outcome.Fetched(_)   => Some(Fetched)
    case library.acquisition.AcquireAttempt.Outcome.NoCopy       => Some(NoCopy)
    case library.acquisition.AcquireAttempt.Outcome.Failed(_)    => Some(Failed)
    case library.acquisition.AcquireAttempt.Outcome.Legacy(_, _) => None
