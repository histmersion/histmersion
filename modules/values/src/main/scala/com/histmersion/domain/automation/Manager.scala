package com.histmersion
package domain.automation

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

object Manager:

  enum Kind(val wire: Kind.Wire) derives CanEqual:
    case Review extends Kind(Kind.Wire("review"))
    case Seed extends Kind(Kind.Wire("seed"))
    case Watch extends Kind(Kind.Wire("watch"))
    case Enrich extends Kind(Kind.Wire("enrich"))

  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[Kind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  enum State(val wire: State.Wire) derives CanEqual:
    case Active extends State(State.Wire("active"))
    case Paused extends State(State.Wire("paused"))
    case Stopped extends State(State.Wire("stopped"))

  object State:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[State] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
