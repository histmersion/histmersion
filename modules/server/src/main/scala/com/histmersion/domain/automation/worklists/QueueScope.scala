package com.histmersion
package domain.automation.worklists

import io.github.iltotore.iron.constraint.any.Pure

/** Which items of a list a listing shows. Every scope reads in id order; only [[All]] shows the `note` blocks. */
enum QueueScope derives CanEqual:
  /** The open items. */
  case Open

  /** Every item, notes included: what an export renders. */
  case All

  /** The items (no notes) agent run or background job `job` wrote or ticked off. */
  case ForJob(job: QueueScope.ForJob.Job)

  /** The items (no notes) that serve goal `goal`: its own, and those its `queue-item` links name (`linked`, the links'
    * references as the goal stores them); open ones only unless `withDone`.
    */
  case ForGoal(goal: QueueScope.ForGoal.Goal, linked: Set[QueueScope.ForGoal.Linked], withDone: QueueScope.ForGoal.WithDone)

object QueueScope:
  object ForJob:
    type Job = Job.T
    object Job extends Newtype[String, Pure]
  object ForGoal:
    type Goal = Goal.T
    object Goal extends Newtype[String, Pure]
    type Linked = Linked.T
    object Linked extends Newtype[String, Pure]
    type WithDone = WithDone.T
    object WithDone extends Newtype[Boolean, Pure]
