package com.histmersion
package domain.automation.autopilot

import com.histmersion.domain.automation.autopilot.AutopilotRepo
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.automation.Managers

/** The autopilot's one row, as the engine's other features reach it: the switch and the managers it paused, when it
  * last ensured the goals' managers and kept house, a person's scheduler strategy, the approval digest's day and the
  * drain. Times are ISO instants (UTC, to the second).
  */
trait AutopilotRowService:
  def row(): AutopilotRepo.Row

  /** Switch it; `paused` = the managers it paused. True = it changed. */
  def set(
      enabled: AutopilotRepo.Row.Enabled,
      by: AutopilotRepo.Row.ChangedBy,
      paused: List[AutopilotRepo.Row.Paused]
  ): Boolean
  def ensured(now: Instant): Unit

  /** Ensure again at the next tick. */
  def ensureSoon(): Unit
  def backedUp(now: Instant): Unit
  def linted(now: Instant): Unit

  /** A paused manager it resumed. */
  def unpaused(managerId: Managers.ManagerInfo.Id): Unit

  /** A person's scheduler strategy: (name, by, at). */
  def strategy(): Option[(AutopilotRepo.StrategyName, AutopilotRepo.Row.ChangedBy, AutopilotRepo.Row.ChangedAt)]

  /** Set (Some) or clear (None) the person's strategy. */
  def setStrategy(name: Option[AutopilotRepo.StrategyName], by: AutopilotRepo.Row.ChangedBy): Unit
  def lastDigest(): Option[AutopilotRepo.Digest]
  def digested(now: Instant): Unit

  /** Draining: since when, by whom, when it was first seen drained. */
  def drain(): Option[(AutopilotRepo.DrainSince, AutopilotRepo.Row.ChangedBy, Option[AutopilotRepo.DrainedAt])]

  /** Start (a drain under way keeps its start) or stop draining. True = it changed. */
  def setDrain(on: Boolean, by: AutopilotRepo.Row.ChangedBy): Boolean

  /** First seen drained at (kept until lifted or work runs again: None). */
  def drained(at: Option[Instant]): Unit

object AutopilotRowService:
  def apply(db: Transactor): AutopilotRowService = Implementation(db)

  final private class Implementation(db: Transactor) extends AutopilotRowService:
    def row(): AutopilotRepo.Row = db.transact(AutopilotRepo.get())
    def set(
        enabled: AutopilotRepo.Row.Enabled,
        by: AutopilotRepo.Row.ChangedBy,
        paused: List[AutopilotRepo.Row.Paused]
    ): Boolean =
      db.transact(AutopilotRepo.set(enabled, by, paused))
    def ensured(now: Instant): Unit = db.transact(AutopilotRepo.ensured(now))
    def ensureSoon(): Unit = db.transact(AutopilotRepo.ensureSoon())
    def backedUp(now: Instant): Unit = db.transact(AutopilotRepo.backedUp(now))
    def linted(now: Instant): Unit = db.transact(AutopilotRepo.linted(now))
    def unpaused(managerId: Managers.ManagerInfo.Id): Unit = db.transact(AutopilotRepo.unpaused(managerId))
    def strategy(): Option[(AutopilotRepo.StrategyName, AutopilotRepo.Row.ChangedBy, AutopilotRepo.Row.ChangedAt)] =
      db.transact(AutopilotRepo.strategy())
    def setStrategy(name: Option[AutopilotRepo.StrategyName], by: AutopilotRepo.Row.ChangedBy): Unit =
      db.transact(AutopilotRepo.setStrategy(name, by))
    def lastDigest(): Option[AutopilotRepo.Digest] = db.transact(AutopilotRepo.lastDigest())
    def digested(now: Instant): Unit = db.transact(AutopilotRepo.digested(now))
    def drain(): Option[(AutopilotRepo.DrainSince, AutopilotRepo.Row.ChangedBy, Option[AutopilotRepo.DrainedAt])] =
      db.transact(AutopilotRepo.drain())
    def setDrain(on: Boolean, by: AutopilotRepo.Row.ChangedBy): Boolean = db.transact(AutopilotRepo.setDrain(on, by))
    def drained(at: Option[Instant]): Unit = db.transact(AutopilotRepo.drained(at))

  /** The row in memory, made at its first use as the database makes it; `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], clock: Clock) extends AutopilotRowService:
    private def now: Instant = Instant.now(clock)
    private def current(): AutopilotRowState =
      state.updateAndGet(s => s.modify(_.autopilot).using(_.orElse(Some(AutopilotRowState.made(now))))).autopilot.get

    /** Change the row by `f` when `when` holds of it; whether it did. */
    private def change(when: AutopilotRowState => Boolean)(f: AutopilotRowState => AutopilotRowState): Boolean =
      val _ = current()
      state
        .getAndUpdate(s => s.modify(_.autopilot).using(_.map(r => if when(r) then f(r) else r)))
        .autopilot
        .exists(when)
    private def always(f: AutopilotRowState => AutopilotRowState): Unit = change(_ => true)(f): Unit
    private def text(at: Instant): String = AutomationState.text(at).value

    def row(): AutopilotRepo.Row =
      val r = current()
      AutopilotRepo.Row(
        AutopilotRepo.Row.Enabled(r.enabled.value),
        AutopilotRepo.Row.ChangedAt(text(r.changedAt)),
        AutopilotRepo.Row.ChangedBy(r.changedBy.value),
        r.paused.map(p => AutopilotRepo.Row.Paused(p.value)),
        r.lastEnsureAt.map(at => AutopilotRepo.Row.LastEnsureAt(text(at))),
        r.lastBackupAt.map(at => AutopilotRepo.Row.LastBackupAt(text(at))),
        r.lastLintAt.map(at => AutopilotRepo.Row.LastLintAt(text(at)))
      )
    def set(
        enabled: AutopilotRepo.Row.Enabled,
        by: AutopilotRepo.Row.ChangedBy,
        paused: List[AutopilotRepo.Row.Paused]
    ): Boolean =
      change(_.enabled.value != enabled.value)(
        _.modify(_.enabled)
          .setTo(AutopilotRowState.Enabled(enabled.value))
          .modify(_.changedAt)
          .setTo(now)
          .modify(_.changedBy)
          .setTo(AutopilotRowState.ChangedBy(by.value))
          .modify(_.paused)
          .setTo(paused.map(p => AutopilotRowState.Paused(p.value)))
          .modify(_.lastEnsureAt)
          .setTo(None)
      )
    def ensured(now: Instant): Unit = always(_.modify(_.lastEnsureAt).setTo(Some(now)))
    def ensureSoon(): Unit = always(_.modify(_.lastEnsureAt).setTo(None))
    def backedUp(now: Instant): Unit = always(_.modify(_.lastBackupAt).setTo(Some(now)))
    def linted(now: Instant): Unit = always(_.modify(_.lastLintAt).setTo(Some(now)))
    def unpaused(managerId: Managers.ManagerInfo.Id): Unit = always(
      _.modify(_.paused).using(_.filterNot(_.value == managerId.value))
    )
    def strategy(): Option[(AutopilotRepo.StrategyName, AutopilotRepo.Row.ChangedBy, AutopilotRepo.Row.ChangedAt)] =
      val r = current()
      r.strategy.map(n =>
        (
          AutopilotRepo.StrategyName(n.value),
          AutopilotRepo.Row.ChangedBy(r.strategyBy.fold("")(_.value)),
          AutopilotRepo.Row.ChangedAt(r.strategyAt.fold("")(text))
        )
      )
    def setStrategy(name: Option[AutopilotRepo.StrategyName], by: AutopilotRepo.Row.ChangedBy): Unit =
      always(
        _.modify(_.strategy)
          .setTo(name.map(n => AutopilotRowState.Strategy(n.value)))
          .modify(_.strategyBy)
          .setTo(Some(AutopilotRowState.StrategyBy(by.value)))
          .modify(_.strategyAt)
          .setTo(Some(now))
      )
    def lastDigest(): Option[AutopilotRepo.Digest] =
      current().lastDigestAt.map(at => AutopilotRepo.Digest(text(at)))
    def digested(now: Instant): Unit = always(_.modify(_.lastDigestAt).setTo(Some(now)))
    def drain(): Option[(AutopilotRepo.DrainSince, AutopilotRepo.Row.ChangedBy, Option[AutopilotRepo.DrainedAt])] =
      val r = current()
      r.drainSince.map(s =>
        (
          AutopilotRepo.DrainSince(text(s)),
          AutopilotRepo.Row.ChangedBy(r.drainBy.fold("")(_.value)),
          r.drainedAt.map(at => AutopilotRepo.DrainedAt(text(at)))
        )
      )
    def setDrain(on: Boolean, by: AutopilotRepo.Row.ChangedBy): Boolean =
      if on then
        change(_.drainSince.isEmpty)(
          _.modify(_.drainSince)
            .setTo(Some(now))
            .modify(_.drainBy)
            .setTo(Some(AutopilotRowState.DrainBy(by.value)))
            .modify(_.drainedAt)
            .setTo(None)
        )
      else
        change(_.drainSince.isDefined)(
          _.modify(_.drainSince).setTo(None).modify(_.drainBy).setTo(None).modify(_.drainedAt).setTo(None)
        )
    def drained(at: Option[Instant]): Unit = change(_.drainSince.isDefined)(_.modify(_.drainedAt).setTo(at)): Unit
