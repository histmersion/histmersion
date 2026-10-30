package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The generated per-runtime trees (docs/research-agent-runtimes.md §2.2): written under `dir` ("" =
  * `<data dir>/agents`) when the server starts if `generate`; `claudeMod` puts the quota-guard mod in the headless
  * Claude Code plugin, `interactiveMod` the reporting mod in the interactive one.
  */
final case class AgentRuntimesConfig(dir: String, generate: Boolean, claudeMod: Boolean, interactiveMod: Boolean)
    derives ConfigReader
