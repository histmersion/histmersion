package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.PlanQuota.Rationale
import io.github.iltotore.iron.constraint.any.Pure

enum Verdict:
  case Usable(note: Option[Usable.Note])

  case Blocked(reason: Rationale, until: Option[Blocked.Until])

  def usable: Boolean = (this.isInstanceOf[Usable])

object Verdict:
  object Usable:
    type Note = Note.T
    object Note extends Newtype[String, Pure]

  object Blocked:
    export Reading.ResetsAt as Until
