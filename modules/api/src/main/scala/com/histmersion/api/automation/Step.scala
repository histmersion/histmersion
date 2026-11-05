package com.histmersion.api.automation

object Step:

  /** How one attempt of a run's step ended, stored in `agent_steps.status`. */
  enum Status(val wire: String):
    case Done extends Status("done")
    case Failed extends Status("failed")

  object Status:
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Step.Status.ParseS): Option[Status] = values.find(_.wire == s)
