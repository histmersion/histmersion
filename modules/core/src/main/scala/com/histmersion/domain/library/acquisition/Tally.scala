package com.histmersion
package domain.library.acquisition

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library.acquisition.Upshot
import com.histmersion.domain.AcquireRecheck.Detail
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.domain.AcquireRecheck.ProgressLine

final case class Tally(
    total: Tally.Total,
    tried: Tally.Tried = Tally.Tried(0),
    closed: Tally.Closed = Tally.Closed(0),
    fetched: Tally.Fetched = Tally.Fetched(0),
    noCopy: Tally.NoCopy = Tally.NoCopy(0),
    failed: Tally.Failed = Tally.Failed(0),
    lastFailure: Option[Detail] = None
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def add(outcome: Upshot, detail: Detail): Tally = outcome match
    case Upshot.Closed  => this.modify(_.closed).setTo(Tally.Closed(closed.value + 1))
    case Upshot.Fetched =>
      this
        .modify(_.tried)
        .setTo(Tally.Tried(tried.value + 1))
        .modify(_.fetched)
        .setTo(Tally.Fetched(fetched.value + 1))
    case Upshot.NoCopy =>
      this.modify(_.tried).setTo(Tally.Tried(tried.value + 1)).modify(_.noCopy).setTo(Tally.NoCopy(noCopy.value + 1))
    case Upshot.Failed =>
      this
        .modify(_.tried)
        .setTo(Tally.Tried(tried.value + 1))
        .modify(_.failed)
        .setTo(Tally.Failed(failed.value + 1))
        .modify(_.lastFailure)
        .setTo(Some(detail))

object Tally:

  type Total = Total.T
  object Total extends Newtype[Int, Pure]
  type Tried = Tried.T
  object Tried extends Newtype[Int, Pure]
  type Closed = Closed.T
  object Closed extends Newtype[Int, Pure]
  type Fetched = Fetched.T
  object Fetched extends Newtype[Int, Pure]
  type NoCopy = NoCopy.T
  object NoCopy extends Newtype[Int, Pure]
  type Failed = Failed.T
  object Failed extends Newtype[Int, Pure]

  extension (t: Tally)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def progressLine: ProgressLine =
      val why =
        t.lastFailure.map(_.value.trim).filter(_.nonEmpty).map(w => if w.length > 60 then w.take(60).trim + " …" else w)
      ProgressLine(
        s"${t.tried}/${t.total} items · ${t.closed} closed · ${t.fetched} fetched · ${t.noCopy} no legal copy · " +
          s"${t.failed} failed" + why.fold("")(w => s" ($w)")
      )
