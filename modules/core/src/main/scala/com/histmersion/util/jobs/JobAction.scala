package com.histmersion.util.jobs

/** What a person asks of a job: pause it (it stops after the part it is on and keeps its progress), resume it (it goes
  * on with the parts it had not done), or cancel it (it stops and is recorded as cancelled).
  */
enum JobAction(val wire: JobAction.Wire):
  case Pause extends JobAction(com.histmersion.util.jobs.JobAction.Wire("pause"))
  case Resume extends JobAction(com.histmersion.util.jobs.JobAction.Wire("resume"))
  case Cancel extends JobAction(com.histmersion.util.jobs.JobAction.Wire("cancel"))

object JobAction:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: Wire): Option[JobAction] = values.find(_.wire == Wire(s.value.trim.toLowerCase))
