package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.automation.Run
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.research.workflows.Status
import com.histmersion.domain.research.workflows.Closure
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.Newtype
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.automation
import com.histmersion.domain.research.workflows.Merged

final case class Entry(
    finding: Finding,
    run: Entry.Run,
    round: Entry.Round,
    status: Status = Status.Open,
    claimed: Entry.Claimed = Entry.Claimed(false),
    notFixed: Option[Entry.NotFixed] = None,
    reopened: Entry.Reopened = Entry.Reopened(0),
    closed: Option[Closure] = None,
    notFixedCount: Entry.NotFixedCount = Entry.NotFixedCount(0),
    deferred: Option[Entry.Deferred] = None
):
  def id: Finding.Id = finding.id
  def open: Boolean = status == Status.Open
  def major: Boolean = finding.major
  def reviewer: Reviewer = finding.reviewer

  def deferredIn(run: Run.Id): Boolean = open && deferred.contains(run)

object Entry:

  export automation.Run.Id as Run
  export Merged.Round
  type Claimed = Claimed.T
  object Claimed extends Newtype[Boolean, Pure]
  type NotFixed = NotFixed.T
  object NotFixed extends Newtype[String, Pure]
  type Reopened = Reopened.T
  object Reopened extends Newtype[Int, Pure]
  type NotFixedCount = NotFixedCount.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object NotFixedCount extends Newtype[Int, Pure]
  export automation.Run.Id as Deferred

  extension (e: Entry)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def closedLine: String =
      val at = Option(e.finding.where.value).map(_.trim).filter(_.nonEmpty).fold("")(w => s" (${w.take(60)})")
      val how = (e.status, e.closed) match
        case (Status.Fixed, Some(c))    => s"fixed, verified by ${c.by} in pass ${c.round}"
        case (Status.Rejected, Some(c)) =>
          s"rejected${c.citekey.fold("")(ck => s" — [[$ck]]: \"${c.quote.value.take(120)}\"")}"
        case (Status.WontFix, Some(c))  => s"won't fix — ${c.note.value.take(120)}"
        case (Status.SetAside, Some(c)) => s"set aside as not material (${c.quote}) — ${c.note.value.take(160)}"
        case (s, _)                     => s.wire.value
      s"${e.id} ${e.finding.severity.wire}$at: ${e.finding.problem.value.trim.take(140)} → $how"
