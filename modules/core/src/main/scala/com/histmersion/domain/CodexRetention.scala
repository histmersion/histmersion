package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** How long the Codex home of the agent runs keeps what each run leaves behind (#2582): every `codex exec` writes a
  * session transcript (`sessions/YYYY/MM/DD/rollout-*.jsonl`) and adds to its SQLite state (`state_*.sqlite`,
  * `thread_history_*.sqlite`), and nothing ever removed them — 1.9 GB after a few weeks. A run needs none of an earlier
  * run's transcripts (each step is a fresh thread), so transcripts older than `keepDays` go; the SQLite files are
  * vacuumed. The login (`auth.json`), the generated config and profiles are never touched: only files under
  * [[SessionDirs]] are candidates. Pure; the server applies it while no run uses the home (the server's
  * `agents.CodexHome`).
  */
@movesTo(FeaturePlace.AutomationAgents)
object CodexRetention:
  type EveryHours = EveryHours.T
  object EveryHours extends Newtype[Int, Pure]
  type FileName = FileName.T
  object FileName extends Newtype[String, Pure]
  type KeepDays = KeepDays.T
  object KeepDays extends Newtype[Int, Pure]
  type LastMs = LastMs.T
  object LastMs extends Newtype[Long, Pure]
  type NowMs = NowMs.T
  object NowMs extends Newtype[Long, Pure]

  /** The directories of run transcripts, relative to the home. */
  type SessionDirs = SessionDirsValue
  type SessionDirsValue = SessionDirsValue.T
  object SessionDirsValue extends Newtype[String, Pure]

  val SessionDirs: List[SessionDirs] = List(SessionDirsValue("sessions"), SessionDirsValue("archived_sessions"))

  /** One file under the home: its path relative to the home (`/`-separated), size, last change. */
  final case class FileInfo(rel: FileInfo.Rel, bytes: FileInfo.Bytes, modifiedMs: FileInfo.ModifiedMs)

  object FileInfo:
    type Rel = Rel.T
    object Rel extends Newtype[String, Pure]
    type Bytes = Bytes.T
    object Bytes extends Newtype[Long, Pure]
    type ModifiedMs = ModifiedMs.T
    object ModifiedMs extends Newtype[Long, Pure]

  private val DayMs = 24L * 60 * 60 * 1000

  /** Pure: is `rel` a file inside a transcript directory (never a file at the home's top level)? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def inSessions(rel: FileInfo.Rel): Boolean =
    val segs = rel.value.replace('\\', '/').split('/').toList.filter(_.nonEmpty)
    segs.size >= 2 && SessionDirs.exists(_.value == segs.head) && !segs.contains("..")

  /** Pure: the transcripts last changed more than `keepDays` before `nowMs` — oldest first. A run in flight appends to
    * its own transcript, so its file is never this old. `keepDays` ≤ 0 keeps all.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stale(files: List[FileInfo], nowMs: NowMs, keepDays: KeepDays): List[FileInfo] =
    if keepDays.value <= 0 then Nil
    else
      val cutoff = nowMs.value - keepDays.value.toLong * DayMs
      files.filter(f => inSessions(f.rel) && f.modifiedMs.value < cutoff).sortBy(f => f.modifiedMs.value: Long)

  /** Pure: the SQLite databases at the home's top level (`state_5.sqlite`), not their `-wal`/`-shm`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sqliteFiles(names: List[FileName]): List[FileName] =
    names.filter(n => !n.value.contains('/') && n.value.endsWith(".sqlite")).sorted

  /** Pure: is a retention pass due — none yet, or the last one `everyHours` or more ago? `everyHours` ≤ 0 turns the
    * automatic pass off.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def due(lastMs: Option[LastMs], nowMs: NowMs, everyHours: EveryHours): Boolean =
    everyHours.value > 0 && lastMs.forall(l => nowMs.value - l.value >= everyHours.value.toLong * 60 * 60 * 1000)
