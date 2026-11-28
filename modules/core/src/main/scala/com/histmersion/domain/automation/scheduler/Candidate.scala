package com.histmersion
package domain.automation.scheduler

import hearth.kindlings.optics.syntax.*
import java.time.Instant
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.automation.scheduler.Block
import com.histmersion.domain.automation.scheduler.JobType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import java.time.Duration
import com.histmersion.domain.automation.scheduler.Settings
import com.histmersion.domain.automation.scheduler.Scored

final case class Candidate(
    id: Candidate.Id,
    managerId: Candidate.ManagerId,
    kind: Candidate.Kind,
    target: Candidate.Target,
    goal: Option[Candidate.Goal] = None,
    goalPriority: Candidate.GoalPriority = Candidate.GoalPriority(0),
    goalCreated: Option[Instant] = None,
    gathering: Candidate.Gathering = Candidate.Gathering(false),
    itemPriority: Candidate.ItemPriority = Candidate.ItemPriority(0),
    waitingSince: Instant = Instant.EPOCH,
    pools: List[Candidate.Pools] = Nil,
    block: Option[Block] = None,
    person: Candidate.Person = Candidate.Person(false)
):
  def jobType: JobType = JobType.of(kind)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def label: String = s"#$id $kind $target"

object Candidate:

  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  type Goal = Goal.T
  object Goal extends Newtype[String, Pure]
  type GoalPriority = GoalPriority.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object GoalPriority extends Newtype[Int, Pure]
  type Gathering = Gathering.T
  object Gathering extends Newtype[Boolean, Pure]
  type ItemPriority = ItemPriority.T
  object ItemPriority extends Newtype[Int, Pure]
  type Pools = Pools.T
  object Pools extends Newtype[String, Pure]
  type Person = Person.T
  object Person extends Newtype[Boolean, Pure]

  extension (c: Candidate)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def score(s: Settings, now: Instant): Scored =
      val mins = math.max(0L, Duration.between(c.waitingSince, now).toMinutes)
      val aging = if s.agingHours.value <= 0 then 0 else (mins / (s.agingHours.value.toLong * 60L)).toInt
      Scored(
        c,
        Scored.WaitedMinutes(mins),
        Scored.Aging(aging),
        Scored.Effective(c.goalPriority.value + aging),
        Scored.Overdue(s.maxWaitHours.value > 0 && mins >= s.maxWaitHours.value.toLong * 60L)
      )
