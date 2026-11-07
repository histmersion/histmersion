package com.histmersion.domain.automation.autopilot

import com.histmersion.domain.automation.autopilot.AutopilotRepo
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference

/** The autopilot's one row, as the engine's other features reach it: the switch and the managers it paused, when it
  * last ensured the goals' managers and kept house, a person's scheduler strategy, the approval digest's day and the
  * drain. Times are ISO instants (UTC, to the second).
  */
trait AutopilotRowService:
  def row(): AutopilotRepo.Row

  /** Switch it; `paused` = the managers it paused. True = it changed. */
  def set(enabled: com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.Enabled, by: com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.ChangedBy, paused: List[com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.Paused]): Boolean
  def ensured(now: Instant): Unit

  /** Ensure again at the next tick. */
  def ensureSoon(): Unit
  def backedUp(now: Instant): Unit
  def linted(now: Instant): Unit

  /** A paused manager it resumed. */
  def unpaused(managerId: com.histmersion.domain.automation.managers.ManagerLight.Id): Unit

  /** A person's scheduler strategy: (name, by, at). */
  def strategy(): Option[(com.histmersion.domain.automation.autopilot.AutopilotRowService.StrategyResultPart1, com.histmersion.domain.automation.autopilot.AutopilotRowService.StrategyResultPart2, com.histmersion.domain.automation.autopilot.AutopilotRowService.StrategyResultPart3)]

  /** Set (Some) or clear (None) the person's strategy. */
  def setStrategy(name: Option[com.histmersion.domain.automation.autopilot.AutopilotRepo.StrategyName], by: com.histmersion.domain.automation.autopilot.AutopilotRowService.SetStrategyBy): Unit
  def lastDigest(): Option[com.histmersion.domain.automation.autopilot.AutopilotRepo.Digest]
  def digested(now: Instant): Unit

  /** Draining: since when, by whom, when it was first seen drained. */
  def drain(): Option[(com.histmersion.domain.automation.autopilot.AutopilotRowService.DrainResultPart1, com.histmersion.domain.automation.autopilot.AutopilotRowService.DrainResultPart2, Option[com.histmersion.domain.automation.autopilot.AutopilotRowService.DrainResultPart3])]

  /** Start (a drain under way keeps its start) or stop draining. True = it changed. */
  def setDrain(on: com.histmersion.domain.automation.autopilot.AutopilotRowService.SetDrainOn, by: com.histmersion.domain.automation.autopilot.AutopilotRowService.SetDrainBy): Boolean

  /** First seen drained at (kept until lifted or work runs again: None). */
  def drained(at: Option[Instant]): Unit

object AutopilotRowService:
  type StrategyResultPart1 = StrategyResultPart1.T
  object StrategyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrategyResultPart2 = StrategyResultPart2.T
  object StrategyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrategyResultPart3 = StrategyResultPart3.T
  object StrategyResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetStrategyBy = SetStrategyBy.T
  object SetStrategyBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainResultPart1 = DrainResultPart1.T
  object DrainResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainResultPart2 = DrainResultPart2.T
  object DrainResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainResultPart3 = DrainResultPart3.T
  object DrainResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetDrainOn = SetDrainOn.T
  object SetDrainOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetDrainBy = SetDrainBy.T
  object SetDrainBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): AutopilotRowService = Implementation(db)

  final private class Implementation(db: Transactor) extends AutopilotRowService:
    def row(): AutopilotRepo.Row = db.transact(AutopilotRepo.get())
    def set(enabled: com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.Enabled, by: com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.ChangedBy, paused: List[com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.Paused]): Boolean =
      db.transact(AutopilotRepo.set(enabled, by, paused))
    def ensured(now: Instant): Unit = db.transact(AutopilotRepo.ensured(now))
    def ensureSoon(): Unit = db.transact(AutopilotRepo.ensureSoon())
    def backedUp(now: Instant): Unit = db.transact(AutopilotRepo.backedUp(now))
    def linted(now: Instant): Unit = db.transact(AutopilotRepo.linted(now))
    def unpaused(managerId: com.histmersion.domain.automation.managers.ManagerLight.Id): Unit = db.transact(AutopilotRepo.unpaused(managerId))
    def strategy(): Option[(com.histmersion.domain.automation.autopilot.AutopilotRowService.Implementation.StrategyResultPart1, com.histmersion.domain.automation.autopilot.AutopilotRowService.Implementation.StrategyResultPart2, com.histmersion.domain.automation.autopilot.AutopilotRowService.Implementation.StrategyResultPart3)] = db.transact(AutopilotRepo.strategy())
    def setStrategy(name: Option[com.histmersion.domain.automation.autopilot.AutopilotRepo.StrategyName], by: com.histmersion.domain.automation.autopilot.AutopilotRowService.Implementation.SetStrategyBy): Unit = db.transact(AutopilotRepo.setStrategy(name, by))
    def lastDigest(): Option[com.histmersion.domain.automation.autopilot.AutopilotRepo.Digest] = db.transact(AutopilotRepo.lastDigest())
    def digested(now: Instant): Unit = db.transact(AutopilotRepo.digested(now))
    def drain(): Option[(com.histmersion.domain.automation.autopilot.AutopilotRowService.Implementation.DrainResultPart1, com.histmersion.domain.automation.autopilot.AutopilotRowService.Implementation.DrainResultPart2, Option[com.histmersion.domain.automation.autopilot.AutopilotRowService.Implementation.DrainResultPart3])] = db.transact(AutopilotRepo.drain())
    def setDrain(on: com.histmersion.domain.automation.autopilot.AutopilotRowService.Implementation.SetDrainOn, by: com.histmersion.domain.automation.autopilot.AutopilotRowService.Implementation.SetDrainBy): Boolean = db.transact(AutopilotRepo.setDrain(on, by))
    def drained(at: Option[Instant]): Unit = db.transact(AutopilotRepo.drained(at))

  object Implementation:
    type StrategyResultPart1 = StrategyResultPart1.T
    object StrategyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StrategyResultPart2 = StrategyResultPart2.T
    object StrategyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StrategyResultPart3 = StrategyResultPart3.T
    object StrategyResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetStrategyBy = SetStrategyBy.T
    object SetStrategyBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DrainResultPart1 = DrainResultPart1.T
    object DrainResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DrainResultPart2 = DrainResultPart2.T
    object DrainResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DrainResultPart3 = DrainResultPart3.T
    object DrainResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetDrainOn = SetDrainOn.T
    object SetDrainOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetDrainBy = SetDrainBy.T
    object SetDrainBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    private def t(at: Option[Instant]): Option[String] = at.map(AutomationState.text)

    def row(): AutopilotRepo.Row =
      val r = current()
      AutopilotRepo.Row(
        r.enabled,
        AutomationState.text(r.changedAt),
        r.changedBy,
        r.paused,
        t(r.lastEnsureAt),
        t(r.lastBackupAt),
        t(r.lastLintAt)
      )
    def set(enabled: com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.Enabled, by: com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.ChangedBy, paused: List[com.histmersion.domain.automation.autopilot.AutopilotRepo.Row.Paused]): Boolean =
      change(_.enabled != enabled)(
        _.modify(_.enabled)
          .setTo(enabled)
          .modify(_.changedAt)
          .setTo(now)
          .modify(_.changedBy)
          .setTo(by)
          .modify(_.paused)
          .setTo(paused)
          .modify(_.lastEnsureAt)
          .setTo(None)
      )
    def ensured(now: Instant): Unit = always(_.modify(_.lastEnsureAt).setTo(Some(now)))
    def ensureSoon(): Unit = always(_.modify(_.lastEnsureAt).setTo(None))
    def backedUp(now: Instant): Unit = always(_.modify(_.lastBackupAt).setTo(Some(now)))
    def linted(now: Instant): Unit = always(_.modify(_.lastLintAt).setTo(Some(now)))
    def unpaused(managerId: com.histmersion.domain.automation.managers.ManagerLight.Id): Unit = always(_.modify(_.paused).using(_.filterNot(_ == managerId)))
    def strategy(): Option[(com.histmersion.domain.automation.autopilot.AutopilotRowService.InMemory.StrategyResultPart1, com.histmersion.domain.automation.autopilot.AutopilotRowService.InMemory.StrategyResultPart2, com.histmersion.domain.automation.autopilot.AutopilotRowService.InMemory.StrategyResultPart3)] =
      val r = current()
      r.strategy.map(n => (n, r.strategyBy.getOrElse(""), t(r.strategyAt).getOrElse("")))
    def setStrategy(name: Option[com.histmersion.domain.automation.autopilot.AutopilotRepo.StrategyName], by: com.histmersion.domain.automation.autopilot.AutopilotRowService.InMemory.SetStrategyBy): Unit =
      always(
        _.modify(_.strategy).setTo(name).modify(_.strategyBy).setTo(Some(by)).modify(_.strategyAt).setTo(Some(now))
      )
    def lastDigest(): Option[com.histmersion.domain.automation.autopilot.AutopilotRepo.Digest] = t(current().lastDigestAt)
    def digested(now: Instant): Unit = always(_.modify(_.lastDigestAt).setTo(Some(now)))
    def drain(): Option[(com.histmersion.domain.automation.autopilot.AutopilotRowService.InMemory.DrainResultPart1, com.histmersion.domain.automation.autopilot.AutopilotRowService.InMemory.DrainResultPart2, Option[com.histmersion.domain.automation.autopilot.AutopilotRowService.InMemory.DrainResultPart3])] =
      val r = current()
      r.drainSince.map(s => (AutomationState.text(s), r.drainBy.getOrElse(""), t(r.drainedAt)))
    def setDrain(on: com.histmersion.domain.automation.autopilot.AutopilotRowService.InMemory.SetDrainOn, by: com.histmersion.domain.automation.autopilot.AutopilotRowService.InMemory.SetDrainBy): Boolean =
      if on then
        change(_.drainSince.isEmpty)(
          _.modify(_.drainSince).setTo(Some(now)).modify(_.drainBy).setTo(Some(by)).modify(_.drainedAt).setTo(None)
        )
      else
        change(_.drainSince.isDefined)(
          _.modify(_.drainSince).setTo(None).modify(_.drainBy).setTo(None).modify(_.drainedAt).setTo(None)
        )
    def drained(at: Option[Instant]): Unit = change(_.drainSince.isDefined)(_.modify(_.drainedAt).setTo(at)): Unit

  object InMemory:
    type StrategyResultPart1 = StrategyResultPart1.T
    object StrategyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StrategyResultPart2 = StrategyResultPart2.T
    object StrategyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StrategyResultPart3 = StrategyResultPart3.T
    object StrategyResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetStrategyBy = SetStrategyBy.T
    object SetStrategyBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DrainResultPart1 = DrainResultPart1.T
    object DrainResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DrainResultPart2 = DrainResultPart2.T
    object DrainResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DrainResultPart3 = DrainResultPart3.T
    object DrainResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetDrainOn = SetDrainOn.T
    object SetDrainOn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetDrainBy = SetDrainBy.T
    object SetDrainBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
