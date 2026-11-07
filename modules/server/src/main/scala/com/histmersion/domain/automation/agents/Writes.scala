package com.histmersion.domain.automation.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** What a role may change: nothing; a typed answer code writes (`propose` — every embedded step); or directly (an
  * interactive role).
  */
enum Writes(val wire: Writes.Wire):
  case None extends Writes(com.histmersion.domain.automation.agents.Writes.Wire("none"))
  case Propose extends Writes(com.histmersion.domain.automation.agents.Writes.Wire("propose"))
  case Direct extends Writes(com.histmersion.domain.automation.agents.Writes.Wire("direct"))

object Writes:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
