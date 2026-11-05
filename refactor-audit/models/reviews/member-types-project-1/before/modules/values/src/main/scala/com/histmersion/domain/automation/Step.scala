package com.histmersion.domain.automation

object Step:

  /** How one attempt of a run's step ended, stored in `agent_steps.status`. */
  enum Status(val wire: String):
    case Done extends Status("done")
    case Failed extends Status("failed")

  object Status:
    def parse(s: String): Option[Status] = values.find(_.wire == s)
