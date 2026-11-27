package com.histmersion
package domain.automation.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** One canonical skill, `skills/<name>/SKILL.md`: its YAML header as written (kept line by line, so the rendered file
  * stays the author's) and its body.
  */
final case class SkillDef(name: SkillDef.Name, header: List[SkillDef.Header], body: SkillDef.Body):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def description: String =
    header.find(_.value.startsWith("description:")).map(_.value.stripPrefix("description:").trim).getOrElse("")

object SkillDef:

  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Header = Header.T
  object Header extends Newtype[String, Pure]
  export AgentDef.Body
