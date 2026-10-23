package com.histmersion.server.agents

import com.histmersion.core.{CodexConfig, CodexRetention, Plural}
import com.histmersion.core.CodexRetention.FileInfo

import java.nio.file.{Files, Path}
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import scala.jdk.CollectionConverters.*

/** Who uses the runs' Codex home, and its retention (#2582, [[CodexRetention]]).
  *
  * Every process Histmersion starts on the home — a step's `codex exec`, a plan read's `codex
  * app-server` — holds the home's SHARED lock while it runs ([[using]]); a retention pass takes the
  * EXCLUSIVE lock without waiting, and when a run holds the home it skips ("in use") rather than
  * delete or vacuum under it. While the pass runs, a new run waits for it. SQLite's own locking guards
  * the vacuum against anything else that has the files open (it then fails, said in the report). */
object CodexHome:

  private val locks = ConcurrentHashMap[Path, ReentrantReadWriteLock]()
  private val lastPass = ConcurrentHashMap[Path, AtomicLong]()

  private def key(home: Path): Path = home.toAbsolutePath.normalize
  private def lock(home: Path): ReentrantReadWriteLock = locks.computeIfAbsent(key(home), _ => ReentrantReadWriteLock())

  /** Run `f` (a Codex process on `home`) holding the home's shared lock. */
  def using[A](home: Path)(f: => A): A =
    val l = lock(home).readLock()
    l.lock()
    try f finally l.unlock()

  /** What a pass did: `skipped` says why it did nothing. */
  final case class Report(skipped: Option[String] = None, deleted: Int = 0, freedBytes: Long = 0,
      vacuumed: List[(String, Long, Long)] = Nil, problems: List[String] = Nil):
    def summary: String = skipped.getOrElse {
      val v = vacuumed.map((n, b, a) => s"$n ${com.histmersion.core.Backup.humanBytes(b)} → ${com.histmersion.core.Backup.humanBytes(a)}")
      (List(s"${Plural.count(deleted, "transcript")} deleted (${com.histmersion.core.Backup.humanBytes(freedBytes)})") ++
        Option.when(v.nonEmpty)(s"vacuumed ${v.mkString(", ")}") ++ problems).mkString("; ")
    }

  /** Every regular file under `dir` (none when it is missing), relative to `home`. */
  private def filesUnder(home: Path, dir: Path): List[FileInfo] =
    if !Files.isDirectory(dir) then Nil
    else
      val s = Files.walk(dir)
      try s.iterator().asScala.filter(p => Files.isRegularFile(p) && !Files.isSymbolicLink(p)).map { p =>
        FileInfo(home.relativize(p).toString.replace('\\', '/'), Files.size(p), Files.getLastModifiedTime(p).toMillis)
      }.toList
      finally s.close()

  /** One pass now: transcripts older than `keep-sessions-days` deleted, the SQLite files vacuumed —
    * or nothing, when a run holds the home. */
  def retain(home: Path, cfg: CodexConfig, nowMs: Long = System.currentTimeMillis(),
      run: List[String] => Either[String, Int] = runCommand): Report =
    val w = lock(home).writeLock()
    if !Files.isDirectory(home) then Report(skipped = Some(s"no Codex home at $home"))
    else if !w.tryLock() then Report(skipped = Some("a Codex run is using the home — not now"))
    else
      try
        val problems = List.newBuilder[String]
        val files = CodexRetention.SessionDirs.flatMap(d => filesUnder(home, home.resolve(d)))
        val gone = CodexRetention.stale(files, nowMs, cfg.keepSessionsDays).filter { f =>
          scala.util.Try(Files.deleteIfExists(home.resolve(f.rel))).fold(e => { problems += s"${f.rel}: ${e.getMessage}"; false }, identity)
        }
        CodexRetention.SessionDirs.foreach(d => pruneEmpty(home.resolve(d)))
        val vacuumed =
          if !cfg.vacuum then Nil
          else
            val names = Option(home.toFile.list()).toList.flatten
            CodexRetention.sqliteFiles(names).flatMap { n =>
              val f = home.resolve(n)
              val before = Files.size(f)
              run(List(cfg.sqlite, f.toString, "VACUUM;")) match
                case Right(0) => Some((n, before, Files.size(f)))
                case Right(c) => problems += s"$n: ${cfg.sqlite} exit $c"; None
                case Left(e)  => problems += s"$n: $e"; None
            }
        Report(None, gone.size, gone.map(_.bytes).sum, vacuumed, problems.result())
      finally w.unlock()

  /** Empty directories under `dir` (not `dir` itself), deepest first. */
  private def pruneEmpty(dir: Path): Unit =
    if Files.isDirectory(dir) then
      val s = Files.walk(dir)
      try s.iterator().asScala.toList.filter(p => p != dir && Files.isDirectory(p)).sortBy(-_.getNameCount)
        .foreach(p => scala.util.Try(Files.delete(p)))   // a non-empty one stays
      finally s.close()

  /** A command's exit code; Left when it cannot start or does not end within 10 minutes. */
  def runCommand(argv: List[String]): Either[String, Int] =
    scala.util.Try {
      val p = ProcessBuilder(argv.asJava).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
      if p.waitFor(10, TimeUnit.MINUTES) then Right(p.exitValue())
      else { p.destroyForcibly(); Left(s"${argv.head} did not finish in 10 minutes") }
    }.toEither.left.map(e => s"${argv.headOption.getOrElse("")}: ${e.getMessage}").flatten

  /** After a step: a pass on a virtual thread when one is due ([[CodexRetention.due]]). */
  def maybeRetain(home: Path, cfg: CodexConfig, nowMs: Long = System.currentTimeMillis()): Unit =
    val last = lastPass.computeIfAbsent(key(home), _ => AtomicLong(0L))
    val prev = last.get()
    if CodexRetention.due(Option.when(prev > 0)(prev), nowMs, cfg.retentionEveryHours) && last.compareAndSet(prev, nowMs) then
      val _ = Thread.ofVirtual().name("codex-retention").start { () =>
        val r = retain(home, cfg)
        if r.skipped.isDefined then last.set(prev)   // try again after the next step
        scribe.info(s"codex home retention ($home): ${r.summary}")
      }
