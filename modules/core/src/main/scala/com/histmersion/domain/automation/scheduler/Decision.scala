package com.histmersion
package domain.automation.scheduler

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.SchedulerPolicy.StartedNote
import com.histmersion.domain.automation.scheduler.Wait
import com.histmersion.domain.automation.scheduler.Candidate
import com.histmersion.domain.automation.scheduler.Scored

final case class Decision(started: List[(Scored, StartedNote)], waiting: List[(Scored, Wait)]):
  def waitOf(id: Candidate.Id): Option[Wait] = waiting.find(_._1.c.id == id).map(_._2)
