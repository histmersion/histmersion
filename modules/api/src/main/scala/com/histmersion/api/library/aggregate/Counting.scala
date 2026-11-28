package com.histmersion
package api.library.aggregate

import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum Counting:
  case Overlap, Starts

object Counting:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Counting] = (s).value.trim.toLowerCase match
    case "starts"       => Some(Starts)
    case "overlap" | "" => Some(Overlap)
    case _              => None
