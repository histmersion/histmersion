package com.histmersion.domain.automation.worklists

/** Which items of a list a listing shows. Every scope reads in id order; only [[All]] shows the `note` blocks. */
enum QueueScope:
  /** The open items. */
  case Open

  /** Every item, notes included: what an export renders. */
  case All

  /** The items (no notes) agent run or background job `job` wrote or ticked off. */
  case ForJob(job: ForJob.Job)

  /** The items (no notes) that serve goal `goal`: its own, and those its `queue-item` links name (`linked`, the links'
    * references as the goal stores them); open ones only unless `withDone`.
    */
  case ForGoal(goal: ForGoal.Goal, linked: Set[ForGoal.Linked], withDone: ForGoal.WithDone)

object QueueScope:
  object ForJob:
    type Job = Job.T
    object Job extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object ForGoal:
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Linked = Linked.T
    object Linked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WithDone = WithDone.T
    object WithDone extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
