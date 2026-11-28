package com.histmersion
package domain.schedulerpolicy

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.SchedulerPolicy.StartedNote
import com.histmersion.domain.schedulerpolicy.Wait
import com.histmersion.domain.schedulerpolicy.Candidate
import com.histmersion.domain.schedulerpolicy.Scored

final case class Decision(started: List[(Scored, StartedNote)], waiting: List[(Scored, Wait)]):
  def waitOf(id: Candidate.Id): Option[Wait] = waiting.find(_._1.c.id == id).map(_._2)
