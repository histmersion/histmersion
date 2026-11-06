package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** The generated per-runtime trees (docs/research-agent-runtimes.md §2.2): written under `dir` ("" =
  * `<data dir>/agents`) when the server starts if `generate`; `claudeMod` puts the quota-guard mod in the headless
  * Claude Code plugin, `interactiveMod` the reporting mod in the interactive one.
  */
final case class AgentRuntimesConfig(dir: AgentRuntimesConfig.Dir, generate: AgentRuntimesConfig.Generate, claudeMod: AgentRuntimesConfig.ClaudeMod, interactiveMod: AgentRuntimesConfig.InteractiveMod)
    derives ConfigReader

object AgentRuntimesConfig:
  type Dir = Dir.T
  object Dir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Generate = Generate.T
  object Generate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClaudeMod = ClaudeMod.T
  object ClaudeMod extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InteractiveMod = InteractiveMod.T
  object InteractiveMod extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
