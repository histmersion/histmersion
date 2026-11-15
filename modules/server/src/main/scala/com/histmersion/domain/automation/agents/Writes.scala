package com.histmersion
package domain.automation.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** What a role may change: nothing; a typed answer code writes (`propose` — every embedded step); or directly (an
  * interactive role).
  */
enum Writes(val wire: Writes.Wire):
  case None extends Writes(Writes.Wire("none"))
  case Propose extends Writes(Writes.Wire("propose"))
  case Direct extends Writes(Writes.Wire("direct"))

object Writes:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
