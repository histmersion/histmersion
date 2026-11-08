package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** Step role → its model CHAIN, `provider:model > provider:model > …` (one model is a chain of one;
  * [[ModelChains.parseChain]]). Empty = no model configured for that role; a step that needs it is refused with a
  * message naming the key. A chain saved in the database (`agent models set`) wins.
  */
final case class AgentRolesConfig(
    probe: AgentRolesConfig.Probe,
    extractor: AgentRolesConfig.Extractor,
    reviewer: AgentRolesConfig.Reviewer,
    coldReader: AgentRolesConfig.ColdReader,
    skeptic: AgentRolesConfig.Skeptic,
    fixer: AgentRolesConfig.Fixer,
    planner: AgentRolesConfig.Planner,
    writer: AgentRolesConfig.Writer
) derives ConfigReader

object AgentRolesConfig:
  type Probe = Probe.T
  object Probe extends Newtype[String, Pure]
  type Extractor = Extractor.T
  object Extractor extends Newtype[String, Pure]
  type Reviewer = Reviewer.T
  object Reviewer extends Newtype[String, Pure]
  type ColdReader = ColdReader.T
  object ColdReader extends Newtype[String, Pure]
  type Skeptic = Skeptic.T
  object Skeptic extends Newtype[String, Pure]
  type Fixer = Fixer.T
  object Fixer extends Newtype[String, Pure]
  type Planner = Planner.T
  object Planner extends Newtype[String, Pure]
  type Writer = Writer.T
  object Writer extends Newtype[String, Pure]
