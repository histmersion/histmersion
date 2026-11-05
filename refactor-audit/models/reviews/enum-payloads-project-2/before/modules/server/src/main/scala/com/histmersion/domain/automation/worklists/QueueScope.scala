package com.histmersion.domain.automation.worklists

/** Which items of a list a listing shows. Every scope reads in id order; only [[All]] shows the `note` blocks. */
enum QueueScope:
  /** The open items. */
  case Open

  /** Every item, notes included: what an export renders. */
  case All

  /** The items (no notes) agent run or background job `job` wrote or ticked off. */
  case ForJob(job: String)

  /** The items (no notes) that serve goal `goal`: its own, and those its `queue-item` links name (`linked`, the links'
    * references as the goal stores them); open ones only unless `withDone`.
    */
  case ForGoal(goal: String, linked: Set[String], withDone: Boolean)
