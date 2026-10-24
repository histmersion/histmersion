package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import java.io.IOException
import java.nio.file.{FileVisitOption, FileVisitResult, Files, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters.*

/** How much a directory holds, measured by a BOUNDED walk — the Storage part of About
  * ([[StorageReport]]) on the server, and the CLI's own locations. Reads the file system and nothing
  * else; writes nothing.
  *
  *  - Symbolic links INSIDE the tree are not followed and not counted: a link to a big directory
  *    elsewhere is that directory's size, not this one's, and a link loop cannot hang the walk. The
  *    root itself may be a link (a configured directory moved to another disk) and is followed.
  *  - A [[Budget]] caps the walk in entries and in time, shared by every walk of one measurement;
  *    a walk it stops comes back `partial` with what it counted so far (a lower bound).
  *  - An unreadable directory is skipped and counted in `unreadable`, never an error. */
object DirSize:

  /** @param exists     the root is there
    * @param partial    the budget ran out before the walk finished
    * @param unreadable directories or files that could not be read */
  final case class Result(bytes: Long, files: Long, exists: Boolean, partial: Boolean = false, unreadable: Int = 0)

  /** The caps of one measurement: at most `maxEntries` visited entries over all its walks, and no
    * walk past `deadlineNanos` (on `clock`'s scale). Thread-safe; one per measurement. */
  final class Budget(val maxEntries: Long, val deadlineNanos: Long, val clock: () => Long = () => System.nanoTime()):
    private val used = AtomicLong(0L)
    /** Count one entry; false once either cap is reached. */
    def take(): Boolean = used.incrementAndGet() <= maxEntries && clock() < deadlineNanos
    def spent: Long = used.get
    def exhausted: Boolean = used.get >= maxEntries || clock() >= deadlineNanos

  object Budget:
    def of(maxEntries: Long, maxMillis: Long): Budget =
      Budget(maxEntries, System.nanoTime() + maxMillis * 1_000_000L)
    val unlimited: Budget = Budget(Long.MaxValue, Long.MaxValue)

  def measure(root: Path, budget: Budget): Result =
    if !Files.exists(root) then Result(0L, 0L, exists = false)
    else if Files.isRegularFile(root) then
      val _ = budget.take()
      Result(scala.util.Try(Files.size(root)).getOrElse(0L), 1L, exists = true)
    else if budget.exhausted then Result(0L, 0L, exists = true, partial = true)
    else
      // the walk's running sums: a visitor is callback-shaped, so it accumulates into locals
      var bytes      = 0L
      var files      = 0L
      var partial    = false
      var unreadable = 0
      val start = scala.util.Try(root.toRealPath()).getOrElse(root)
      val visitor = new SimpleFileVisitor[Path]:
        private def step(): FileVisitResult =
          if budget.take() then FileVisitResult.CONTINUE
          else { partial = true; FileVisitResult.TERMINATE }
        override def preVisitDirectory(d: Path, a: BasicFileAttributes): FileVisitResult = step()
        override def visitFile(f: Path, a: BasicFileAttributes): FileVisitResult =
          if a.isRegularFile then { bytes += a.size; files += 1 }
          step()
        override def visitFileFailed(f: Path, e: IOException): FileVisitResult =
          unreadable += 1
          FileVisitResult.CONTINUE
        override def postVisitDirectory(d: Path, e: IOException): FileVisitResult =
          if e != null then unreadable += 1
          FileVisitResult.CONTINUE
      try
        val _ = Files.walkFileTree(start, java.util.EnumSet.noneOf(classOf[FileVisitOption]), Int.MaxValue, visitor)
      catch case _: IOException => unreadable += 1
      Result(bytes, files, exists = true, partial, unreadable)

  /** `p`, or the nearest of its parents that exists (a directory not made yet is on its parent's volume). */
  def existing(p: Path): Option[Path] =
    Iterator.iterate(p.toAbsolutePath)(_.getParent).takeWhile(_ != null).find(Files.exists(_))

  /** The volume `p` is (or would be) on, with its size and the space this process may still use. */
  def volume(p: Path): Option[StorageVolume] =
    existing(p).flatMap { e =>
      scala.util.Try {
        val s = Files.getFileStore(e)
        StorageVolume(s.toString, s.getTotalSpace, s.getUsableSpace)
      }.toOption
    }

  /** The children of `dir` (not following a link), sorted by name; empty when it is no directory. */
  def children(dir: Path): List[Path] =
    if !Files.isDirectory(dir) then Nil
    else
      scala.util.Try {
        val s = Files.list(dir)
        try s.iterator().asScala.toList.sortBy(_.getFileName.toString) finally s.close()
      }.getOrElse(Nil)
