package com.histmersion
package domain.autopilotpolicy

import com.histmersion.domain.AutopilotPolicy.Rationale
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.AutopilotPolicy.Note
import com.histmersion.domain.AutopilotPolicy.NotePrefix

enum Verdict:
  case Approve(reason: Rationale)
  case Reject(reason: Rationale)
  case Leave(why: Rationale)

object Verdict:
  extension (v: Verdict)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def note: Option[Note] = v match
      case Verdict.Approve(r) => Some(Note(s"$NotePrefix approved — $r"))
      case Verdict.Reject(r)  => Some(Note(s"$NotePrefix rejected — $r"))
      case Verdict.Leave(_)   => None
