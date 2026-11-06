package com.histmersion.domain

/** How long the Codex home of the agent runs keeps what each run leaves behind (#2582): every `codex exec` writes a
  * session transcript (`sessions/YYYY/MM/DD/rollout-*.jsonl`) and adds to its SQLite state (`state_*.sqlite`,
  * `thread_history_*.sqlite`), and nothing ever removed them — 1.9 GB after a few weeks. A run needs none of an earlier
  * run's transcripts (each step is a fresh thread), so transcripts older than `keepDays` go; the SQLite files are
  * vacuumed. The login (`auth.json`), the generated config and profiles are never touched: only files under
  * [[SessionDirs]] are candidates. Pure; the server applies it while no run uses the home (the server's
  * `agents.CodexHome`).
  */
object CodexRetention:
  type EveryHours = EveryHours.T
  object EveryHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileName = FileName.T
  object FileName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeepDays = KeepDays.T
  object KeepDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastMs = LastMs.T
  object LastMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NowMs = NowMs.T
  object NowMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The directories of run transcripts, relative to the home. */
  type SessionDirs = SessionDirsValue
  type SessionDirsValue = SessionDirsValue.T
  object SessionDirsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val SessionDirs: List[com.histmersion.domain.CodexRetention.SessionDirs] = List(com.histmersion.domain.CodexRetention.SessionDirsValue("sessions"), com.histmersion.domain.CodexRetention.SessionDirsValue("archived_sessions"))

  /** One file under the home: its path relative to the home (`/`-separated), size, last change. */
  final case class FileInfo(rel: FileInfo.Rel, bytes: FileInfo.Bytes, modifiedMs: FileInfo.ModifiedMs)

  object FileInfo:
    type Rel = Rel.T
    object Rel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bytes = Bytes.T
    object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ModifiedMs = ModifiedMs.T
    object ModifiedMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val DayMs = 24L * 60 * 60 * 1000

  /** Pure: is `rel` a file inside a transcript directory (never a file at the home's top level)? */
  def inSessions(rel: com.histmersion.domain.CodexRetention.FileInfo.Rel): Boolean =
    val segs = rel.value.replace('\\', '/').split('/').toList.filter(_.nonEmpty)
    segs.size >= 2 && SessionDirs.exists(_.value == segs.head) && !segs.contains("..")

  /** Pure: the transcripts last changed more than `keepDays` before `nowMs` — oldest first. A run in flight appends to
    * its own transcript, so its file is never this old. `keepDays` ≤ 0 keeps all.
    */
  def stale(files: List[FileInfo], nowMs: com.histmersion.domain.CodexRetention.NowMs, keepDays: com.histmersion.domain.CodexRetention.KeepDays): List[FileInfo] =
    if keepDays.value <= 0 then Nil
    else
      val cutoff = nowMs.value - keepDays.value.toLong * DayMs
      files.filter(f => inSessions(f.rel) && f.modifiedMs.value < cutoff).sortBy(f => f.modifiedMs.value: Long)

  /** Pure: the SQLite databases at the home's top level (`state_5.sqlite`), not their `-wal`/`-shm`. */
  def sqliteFiles(names: List[com.histmersion.domain.CodexRetention.FileName]): List[com.histmersion.domain.CodexRetention.FileName] =
    names.filter(n => !n.value.contains('/') && n.value.endsWith(".sqlite")).sortBy(_.value)

  /** Pure: is a retention pass due — none yet, or the last one `everyHours` or more ago? `everyHours` ≤ 0 turns the
    * automatic pass off.
    */
  def due(lastMs: Option[com.histmersion.domain.CodexRetention.LastMs], nowMs: com.histmersion.domain.CodexRetention.NowMs, everyHours: com.histmersion.domain.CodexRetention.EveryHours): Boolean =
    everyHours.value > 0 && lastMs.forall(l => nowMs.value - l.value >= everyHours.value.toLong * 60 * 60 * 1000)
