package com.histmersion
package domain.personal.seen

import com.histmersion.domain.personal.{MarkTime, PersonalState}
import com.histmersion.util.database.Transactor
import com.histmersion.UserId

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{tolerated, LintRule}

/** How far a person has seen each window's news: a mark that only moves forward. The table keeps one mark per board
  * while the server runs for one person, so every user's marks are the local person's until it gains a user column.
  */
trait SeenService:
  def marks(user: UserId): Map[SeenBoard, MarkTime]

  /** The person saw `board` up to `at` (now when none); the mark kept, which never moves back. */
  def mark(user: UserId, board: SeenBoard, at: Option[Instant])(using WritePermit): MarkTime

object SeenService:

  def apply(db: Transactor): SeenService = Implementation(db)

  final private class Implementation(db: Transactor) extends SeenService:
    def marks(user: UserId): Map[SeenBoard, MarkTime] = db.read(SeenRepo.all)
    def mark(user: UserId, board: SeenBoard, at: Option[Instant])(using WritePermit): MarkTime =
      db.write(SeenRepo.mark(board, at))

  /** The marks held in `state`; `clock` stands in for the database's now(). */
  final class InMemory(state: AtomicReference[PersonalState], clock: Clock) extends SeenService:
    def marks(user: UserId): Map[SeenBoard, MarkTime] = state.get.seen

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def mark(user: UserId, board: SeenBoard, at: Option[Instant])(using WritePermit): MarkTime =
      val asked = MarkTime.at(at.getOrElse(Instant.now(clock)))
      state
        .updateAndGet(
          _.modify(_.seen).using(s => s + (board -> s.get(board).filter(_.value.isAfter(asked.value)).getOrElse(asked)))
        )
        .seen(board)
