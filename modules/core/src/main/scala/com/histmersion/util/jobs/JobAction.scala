package com.histmersion.util.jobs

/** What a person asks of a job: pause it (it stops after the part it is on and keeps its progress), resume it (it goes
  * on with the parts it had not done), or cancel it (it stops and is recorded as cancelled).
  */
enum JobAction(val wire: String):
  case Pause extends JobAction("pause")
  case Resume extends JobAction("resume")
  case Cancel extends JobAction("cancel")

object JobAction:
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.util.jobs.JobAction.ParseS): Option[JobAction] = values.find(_.wire == s.trim.toLowerCase)
