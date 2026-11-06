package com.histmersion.api.automation

object Step:

  /** How one attempt of a run's step ended, stored in `agent_steps.status`. */
  enum Status(val wire: Status.Wire):
    case Done extends Status(_root_.com.histmersion.api.automation.Step.Status.Wire("done"))
    case Failed extends Status(_root_.com.histmersion.api.automation.Step.Status.Wire("failed"))

  object Status:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def parse(s: com.histmersion.api.automation.Step.Status.Wire): Option[Status] = values.find(_.wire == s)
