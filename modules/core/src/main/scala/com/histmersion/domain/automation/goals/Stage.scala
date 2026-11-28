package com.histmersion
package domain.automation.goals

import hearth.kindlings.optics.syntax.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.Newtype

enum Stage(val rank: Stage.Rank, val wire: Stage.Wire) derives CanEqual:
  case Reviewing extends Stage(Stage.Rank(0), Stage.Wire("reviewing"))
  case Compiling extends Stage(Stage.Rank(1), Stage.Wire("compiling"))
  case Ready extends Stage(Stage.Rank(2), Stage.Wire("ready"))
  case Gathering extends Stage(Stage.Rank(3), Stage.Wire("gathering"))

object Stage:
  type Rank = Rank.T
  object Rank extends Newtype[Int, Pure]
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Stage] = values.find(_.wire == Wire(s.value.trim.toLowerCase))
