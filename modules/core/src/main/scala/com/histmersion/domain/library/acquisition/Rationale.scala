package com.histmersion
package domain.library.acquisition

import com.histmersion.util.problem.Text.line
import com.histmersion.util.problem.Text.sentences
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problems
import hearth.kindlings.optics.syntax.*
import java.time.LocalDate
import com.histmersion.domain.library.Citekey
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library
import java.time.ZoneOffset
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

enum Rationale:
  case Closed(day: LocalDate, citekey: Citekey)
  case Fetched(day: LocalDate, source: library.acquisition.AcquireAttempt.Outcome.Source)
  case NoCopy(day: LocalDate, answers: Vector[Problem.NotFound.Answer])
  case Failed(day: LocalDate, why: Problems)
  case Legacy(day: LocalDate, note: library.acquisition.AcquireAttempt.Outcome.LegacyNote)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def said: String =
    val text = this match
      case Closed(day, citekey) =>
        s"checked $day: in the corpus with its original as [[$citekey]] — closed"
      case Fetched(day, source) =>
        s"checked $day: a legal copy found ($source) — converting; the item closes once it is stored"
      case NoCopy(day, answers) =>
        s"checked $day: no legal copy" + (if answers.isEmpty then ""
                                          else answers.map(_.line).mkString(" (", "; ", ")"))
      case Failed(day, why) => s"checked $day: no answer (${why.sentences.mkString("; ")})"
      case Legacy(_, note)  => s"$note"
    text.take(500)

object Rationale:
  type Said = Said.T
  object Said extends Newtype[String, Pure]:
    def of(rationale: Rationale): Said = Said(rationale.said)

  def of(attempt: library.acquisition.AcquireAttempt): Rationale =
    val day = LocalDate.ofInstant(attempt.at, ZoneOffset.UTC)
    attempt.outcome match
      case library.acquisition.AcquireAttempt.Outcome.Closed(ck)      => Closed(day, ck)
      case library.acquisition.AcquireAttempt.Outcome.Fetched(source) => Fetched(day, source)
      case library.acquisition.AcquireAttempt.Outcome.NoCopy          => NoCopy(day, attempt.answers)
      case library.acquisition.AcquireAttempt.Outcome.Failed(why)     => Failed(day, why)
      case library.acquisition.AcquireAttempt.Outcome.Legacy(note, _) => Legacy(day, note)
