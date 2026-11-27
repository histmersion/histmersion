package com.histmersion
package cli

import com.histmersion.api.given

import java.io.IOException
import java.nio.file.{Files, FileVisitOption, FileVisitResult, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters.*
import com.histmersion.api.maintenance.Storage
import com.histmersion.api.ByteCount
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.config.StorageConfig
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** How much a directory holds, measured by a BOUNDED walk — the Storage part of About ([[StorageReport]]) on the
  * server, and the CLI's own locations. Reads the file system and nothing else; writes nothing.
  *
  *   - Symbolic links INSIDE the tree are not followed and not counted: a link to a big directory elsewhere is that
  *     directory's size, not this one's, and a link loop cannot hang the walk. The root itself may be a link (a
  *     configured directory moved to another disk) and is followed.
  *   - A [[Budget]] caps the walk in entries and in time, shared by every walk of one measurement; a walk it stops
  *     comes back `partial` with what it counted so far (a lower bound).
  *   - An unreadable directory is skipped and counted in `unreadable`, never an error.
  */
@atBoundary(BoundaryKind.Process, "walks a directory on disk to measure how much it holds")
object DirSize:

  /** @param exists
    *   the root is there
    * @param partial
    *   the budget ran out before the walk finished
    * @param unreadable
    *   directories or files that could not be read
    */
  final case class Result(
      bytes: Result.Bytes,
      files: Result.Files,
      exists: Result.Exists,
      partial: Result.Partial = Result.Partial(false),
      unreadable: Result.Unreadable = Result.Unreadable(0)
  )

  object Result:
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Bytes = ByteCount
    val Bytes: ByteCount.type = ByteCount
    export Storage.FileCount as Files
    export Storage.Entry.{Exists, Partial}
    type Unreadable = Unreadable.T
    object Unreadable extends Newtype[Int, Pure]

  /** The caps of one measurement: at most `maxEntries` visited entries over all its walks, and no walk past
    * `deadlineNanos` (on `clock`'s scale). Thread-safe; one per measurement.
    */
  final class Budget(
      val maxEntries: Budget.MaxEntries,
      val deadlineNanos: Budget.DeadlineNanos,
      val clock: () => Long = () => System.nanoTime()
  ):
    private val used = AtomicLong(0L)

    /** Count one entry; false once either cap is reached. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def take(): Boolean = used.incrementAndGet() <= maxEntries.value && clock() < deadlineNanos.value
    def spent: Long = used.get
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def exhausted: Boolean = used.get >= maxEntries.value || clock() >= deadlineNanos.value

  object Budget:
    type MaxEntries = MaxEntries.T
    object MaxEntries extends Newtype[Long, Pure]
    type DeadlineNanos = DeadlineNanos.T
    object DeadlineNanos extends Newtype[Long, Pure]

    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def of(maxEntries: StorageConfig.MaxEntries, maxMillis: StorageConfig.MaxMillis): Budget =
      Budget(
        MaxEntries(maxEntries.value.toLong),
        DeadlineNanos(System.nanoTime() + maxMillis.value.toLong * 1_000_000L)
      )
    val unlimited: Budget = Budget(MaxEntries(Long.MaxValue), DeadlineNanos(Long.MaxValue))

  def measure(root: Path, budget: Budget): Result =
    if !Files.exists(root) then Result(Result.Bytes(0L), Result.Files(0L), exists = Result.Exists(false))
    else if Files.isRegularFile(root) then
      val _ = budget.take()
      Result(
        Result.Bytes(scala.util.Try(Files.size(root)).getOrElse(0L)),
        Result.Files(1L),
        exists = Result.Exists(true)
      )
    else if budget.exhausted then
      Result(Result.Bytes(0L), Result.Files(0L), exists = Result.Exists(true), partial = Result.Partial(true))
    else
      // the walk's running sums: a visitor is callback-shaped, so it accumulates into locals
      var bytes = 0L
      var files = 0L
      var partial = false
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
      Result(
        Result.Bytes(bytes),
        Result.Files(files),
        exists = Result.Exists(true),
        Result.Partial(partial),
        Result.Unreadable(unreadable)
      )

  /** `p`, or the nearest of its parents that exists (a directory not made yet is on its parent's volume). */
  def existing(p: Path): Option[Path] =
    Iterator.iterate(p.toAbsolutePath)(_.getParent).takeWhile(_ != null).find(Files.exists(_))

  /** The volume `p` is (or would be) on, with its size and the space this process may still use. */
  def volume(p: Path): Option[Storage.Volume] =
    existing(p).flatMap { e =>
      scala.util.Try {
        val s = Files.getFileStore(e)
        Storage.Volume(Storage.Volume.Id(s.toString), ByteCount(s.getTotalSpace), ByteCount(s.getUsableSpace))
      }.toOption
    }

  /** The children of `dir` (not following a link), sorted by name; empty when it is no directory. */
  def children(dir: Path): List[Path] =
    if !Files.isDirectory(dir) then Nil
    else
      scala.util
        .Try {
          val s = Files.list(dir)
          try s.iterator().asScala.toList.sortBy(_.getFileName.toString)
          finally s.close()
        }
        .getOrElse(Nil)
