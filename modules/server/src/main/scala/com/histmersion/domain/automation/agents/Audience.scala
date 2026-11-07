package com.histmersion.domain.automation.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** Who a marked part of a definition is for (`<!-- only:<audience> -->` … `<!-- /only:<audience> -->`): the person's
  * own session, which runs the `histmersion` CLI in Bash and writes its answer as the JSON block of its Output section;
  * a headless step, which calls MCP tools and answers through the response schema; and the writer of an article — the
  * parts of the writing guide (its phases, its self-check) that a reviewer or a fixer does not act on. Every turn of a
  * step re-sends its prompt, so what a role does not act on is left out of it (docs/research-agent-runtimes.md §4).
  */
enum Audience(val wire: Audience.Wire):
  case Interactive extends Audience(com.histmersion.domain.automation.agents.Audience.Wire("interactive"))
  case Headless extends Audience(com.histmersion.domain.automation.agents.Audience.Wire("headless"))
  case Writer extends Audience(com.histmersion.domain.automation.agents.Audience.Wire("writer"))

object Audience:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
