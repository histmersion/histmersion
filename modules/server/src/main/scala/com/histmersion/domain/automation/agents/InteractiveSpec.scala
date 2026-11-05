package com.histmersion.domain.automation.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** How the agent shows in the person's own Claude Code (the interactive plugin): its model and its built-in tools,
  * exactly as the hand-written definitions had them.
  */
final case class InteractiveSpec(model: InteractiveSpec.Model, tools: List[InteractiveSpec.Tools])

object InteractiveSpec:
  type Model = Model.T
  object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tools = Tools.T
  object Tools extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
