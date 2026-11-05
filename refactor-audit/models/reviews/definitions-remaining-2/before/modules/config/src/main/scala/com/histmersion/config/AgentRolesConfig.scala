package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Step role → its model CHAIN, `provider:model > provider:model > …` (one model is a chain of one;
  * [[ModelChains.parseChain]]). Empty = no model configured for that role; a step that needs it is refused with a
  * message naming the key. A chain saved in the database (`agent models set`) wins.
  */
final case class AgentRolesConfig(
    probe: String,
    extractor: String,
    reviewer: String,
    coldReader: String,
    skeptic: String,
    fixer: String,
    planner: String,
    writer: String
) derives ConfigReader
