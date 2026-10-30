package com.histmersion.domain.jobs

/** What a person asks of a job: pause it (it stops after the part it is on and keeps its progress), resume it (it goes
  * on with the parts it had not done), or cancel it (it stops and is recorded as cancelled).
  */
enum JobAction(val wire: String):
  case Pause extends JobAction("pause")
  case Resume extends JobAction("resume")
  case Cancel extends JobAction("cancel")

object JobAction:
  def parse(s: String): Option[JobAction] = values.find(_.wire == s.trim.toLowerCase)
