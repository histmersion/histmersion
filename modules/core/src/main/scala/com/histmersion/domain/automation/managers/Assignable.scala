package com.histmersion
package domain.automation.managers

import com.histmersion.api.given
import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class Assignable(
    id: Assignable.Id,
    target: Assignable.Target,
    priority: Assignable.Priority,
    notBefore: Option[Instant]
)

object Assignable:
  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Priority = Priority.T
  object Priority extends Newtype[Int, Pure]

  extension (c: Assignable) def due(now: Instant): Boolean = c.notBefore.forall(!_.isAfter(now))
