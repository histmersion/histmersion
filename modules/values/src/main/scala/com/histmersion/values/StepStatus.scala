package com.histmersion.values

/** How one attempt of a run's step ended, stored in `agent_steps.status`. */
enum StepStatus(val wire: String):
  case Done extends StepStatus("done")
  case Failed extends StepStatus("failed")

object StepStatus:
  def parse(s: String): Option[StepStatus] = values.find(_.wire == s)
