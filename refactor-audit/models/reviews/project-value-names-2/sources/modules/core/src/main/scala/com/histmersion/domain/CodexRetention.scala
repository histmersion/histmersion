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

  /** The directories of run transcripts, relative to the home. */
  type SessionDirs = SessionDirsValue
  type SessionDirsValue = SessionDirsValue.T
  object SessionDirsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InSessionsResult = InSessionsResult.T
  object InSessionsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InSessionsRel = InSessionsRel.T
  object InSessionsRel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleNowMs = StaleNowMs.T
  object StaleNowMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleKeepDays = StaleKeepDays.T
  object StaleKeepDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SqliteFilesResult = SqliteFilesResult.T
  object SqliteFilesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SqliteFilesNames = SqliteFilesNames.T
  object SqliteFilesNames extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueResult = DueResult.T
  object DueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueLastMs = DueLastMs.T
  object DueLastMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueNowMs = DueNowMs.T
  object DueNowMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueEveryHours = DueEveryHours.T
  object DueEveryHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val SessionDirs: List[com.histmersion.domain.CodexRetention.SessionDirs] = List("sessions", "archived_sessions")

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
  def inSessions(rel: com.histmersion.domain.CodexRetention.InSessionsRel): com.histmersion.domain.CodexRetention.InSessionsResult =
    val segs = rel.replace('\\', '/').split('/').toList.filter(_.nonEmpty)
    segs.size >= 2 && SessionDirs.contains(segs.head) && !segs.contains("..")

  /** Pure: the transcripts last changed more than `keepDays` before `nowMs` — oldest first. A run in flight appends to
    * its own transcript, so its file is never this old. `keepDays` ≤ 0 keeps all.
    */
  def stale(files: List[FileInfo], nowMs: com.histmersion.domain.CodexRetention.StaleNowMs, keepDays: com.histmersion.domain.CodexRetention.StaleKeepDays): List[FileInfo] =
    if keepDays <= 0 then Nil
    else
      val cutoff = nowMs - keepDays.toLong * DayMs
      files.filter(f => inSessions(f.rel) && f.modifiedMs < cutoff).sortBy(_.modifiedMs)

  /** Pure: the SQLite databases at the home's top level (`state_5.sqlite`), not their `-wal`/`-shm`. */
  def sqliteFiles(names: List[com.histmersion.domain.CodexRetention.SqliteFilesNames]): List[com.histmersion.domain.CodexRetention.SqliteFilesResult] =
    names.filter(n => !n.contains('/') && n.endsWith(".sqlite")).sorted

  /** Pure: is a retention pass due — none yet, or the last one `everyHours` or more ago? `everyHours` ≤ 0 turns the
    * automatic pass off.
    */
  def due(lastMs: Option[com.histmersion.domain.CodexRetention.DueLastMs], nowMs: com.histmersion.domain.CodexRetention.DueNowMs, everyHours: com.histmersion.domain.CodexRetention.DueEveryHours): com.histmersion.domain.CodexRetention.DueResult =
    everyHours > 0 && lastMs.forall(l => nowMs - l >= everyHours.toLong * 60 * 60 * 1000)
