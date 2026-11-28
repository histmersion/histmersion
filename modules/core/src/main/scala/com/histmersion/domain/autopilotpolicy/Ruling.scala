package com.histmersion
package domain.autopilotpolicy

import com.histmersion.domain.AutopilotPolicy.Rationale
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.AutopilotPolicy.Note
import com.histmersion.domain.AutopilotPolicy.NotePrefix

enum Ruling:
  case Approve(reason: Rationale)
  case Reject(reason: Rationale)
  case Leave(why: Rationale)

object Ruling:
  extension (v: Ruling)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def note: Option[Note] = v match
      case Ruling.Approve(r) => Some(Note(s"$NotePrefix approved — $r"))
      case Ruling.Reject(r)  => Some(Note(s"$NotePrefix rejected — $r"))
      case Ruling.Leave(_)   => None
