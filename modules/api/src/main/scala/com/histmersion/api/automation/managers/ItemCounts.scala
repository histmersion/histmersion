package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import scala.annotation.tailrec
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Managers.ItemStatus

final case class ItemCounts(
    pending: ItemCounts.Pending = ItemCounts.Pending(0),
    held: ItemCounts.Held = ItemCounts.Held(0),
    active: ItemCounts.Active = ItemCounts.Active(0),
    done: ItemCounts.Done = ItemCounts.Done(0),
    stopped: ItemCounts.Stopped = ItemCounts.Stopped(0),
    failed: ItemCounts.Failed = ItemCounts.Failed(0),
    cancelled: ItemCounts.Cancelled = ItemCounts.Cancelled(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def open: ItemCounts.Open = ItemCounts.Open((pending).value + (held).value + (active).value)

object ItemCounts:
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Open = Open.T
  object Open extends Newtype[Int, Pure]

  type Pending = Pending.T
  object Pending extends Newtype[Int, Pure]
  type Held = Held.T
  object Held extends Newtype[Int, Pure]
  type Active = Active.T
  object Active extends Newtype[Int, Pure]
  type Done = Done.T
  object Done extends Newtype[Int, Pure]
  type Stopped = Stopped.T
  object Stopped extends Newtype[Int, Pure]
  type Failed = Failed.T
  object Failed extends Newtype[Int, Pure]
  type Cancelled = Cancelled.T
  object Cancelled extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(rows: List[(api.automation.ItemStatus.Wire, Count)]): ItemCounts =
    @tailrec def loop(
        rest: List[(api.automation.ItemStatus.Wire, Count)],
        pending: Pending,
        held: Held,
        active: Active,
        done: Done,
        stopped: Stopped,
        failed: Failed,
        cancelled: Cancelled
    ): ItemCounts =
      rest match
        case Nil            => ItemCounts(pending, held, active, done, stopped, failed, cancelled)
        case (s, n) :: tail =>
          ItemStatus.parse(s) match
            case Some(ItemStatus.Pending) =>
              loop(tail, Pending((pending).value + (n).value), held, active, done, stopped, failed, cancelled)
            case Some(ItemStatus.Held) =>
              loop(tail, pending, Held((held).value + (n).value), active, done, stopped, failed, cancelled)
            case Some(ItemStatus.Active) =>
              loop(tail, pending, held, Active((active).value + (n).value), done, stopped, failed, cancelled)
            case Some(ItemStatus.Done) =>
              loop(tail, pending, held, active, Done((done).value + (n).value), stopped, failed, cancelled)
            case Some(ItemStatus.Stopped) =>
              loop(tail, pending, held, active, done, Stopped((stopped).value + (n).value), failed, cancelled)
            case Some(ItemStatus.Failed) =>
              loop(tail, pending, held, active, done, stopped, Failed((failed).value + (n).value), cancelled)
            case Some(ItemStatus.Cancelled) =>
              loop(tail, pending, held, active, done, stopped, failed, Cancelled((cancelled).value + (n).value))
            case None => loop(tail, pending, held, active, done, stopped, failed, cancelled)
    loop(rows, Pending(0), Held(0), Active(0), Done(0), Stopped(0), Failed(0), Cancelled(0))
