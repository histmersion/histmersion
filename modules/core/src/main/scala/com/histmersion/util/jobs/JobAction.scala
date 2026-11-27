package com.histmersion
package util.jobs

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** What a person asks of a job: pause it (it stops after the part it is on and keeps its progress), resume it (it goes
  * on with the parts it had not done), or cancel it (it stops and is recorded as cancelled).
  */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
enum JobAction(val wire: JobAction.Wire):
  case Pause extends JobAction(JobAction.Wire("pause"))
  case Resume extends JobAction(JobAction.Wire("resume"))
  case Cancel extends JobAction(JobAction.Wire("cancel"))

object JobAction:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[JobAction] = values.find(_.wire == Wire(s.value.trim.toLowerCase))
