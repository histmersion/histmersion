package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** The generated per-runtime trees (docs/research-agent-runtimes.md §2.2): written under `dir` ("" =
  * `<data dir>/agents`) when the server starts if `generate`; `claudeMod` puts the quota-guard mod in the headless
  * Claude Code plugin, `interactiveMod` the reporting mod in the interactive one.
  */
final case class AgentRuntimesConfig(
    dir: AgentRuntimesConfig.Dir,
    generate: AgentRuntimesConfig.Generate,
    claudeMod: AgentRuntimesConfig.ClaudeMod,
    interactiveMod: AgentRuntimesConfig.InteractiveMod
) derives ConfigReader

object AgentRuntimesConfig:
  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]
  type Generate = Generate.T
  object Generate extends Newtype[Boolean, Pure]
  type ClaudeMod = ClaudeMod.T
  object ClaudeMod extends Newtype[Boolean, Pure]
  type InteractiveMod = InteractiveMod.T
  object InteractiveMod extends Newtype[Boolean, Pure]
