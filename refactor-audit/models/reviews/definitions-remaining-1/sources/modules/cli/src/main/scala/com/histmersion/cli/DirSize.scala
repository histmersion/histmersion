package com.histmersion.cli

import com.histmersion.api.given

import java.io.IOException
import java.nio.file.{Files, FileVisitOption, FileVisitResult, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters.*
import com.histmersion.api.maintenance.Storage

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
object DirSize:

  /** @param exists
    *   the root is there
    * @param partial
    *   the budget ran out before the walk finished
    * @param unreadable
    *   directories or files that could not be read
    */
  final case class Result(bytes: Result.Bytes, files: Result.Files, exists: Result.Exists, partial: Result.Partial = false, unreadable: Result.Unreadable = 0)

  object Result:
    type Bytes = Bytes.T
    object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Files = Files.T
    object Files extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Exists = Exists.T
    object Exists extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Partial = Partial.T
    object Partial extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unreadable = Unreadable.T
    object Unreadable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The caps of one measurement: at most `maxEntries` visited entries over all its walks, and no walk past
    * `deadlineNanos` (on `clock`'s scale). Thread-safe; one per measurement.
    */
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
      Result(bytes, files, exists = true, partial, unreadable)

  /** `p`, or the nearest of its parents that exists (a directory not made yet is on its parent's volume). */
  def existing(p: Path): Option[Path] =
    Iterator.iterate(p.toAbsolutePath)(_.getParent).takeWhile(_ != null).find(Files.exists(_))

  /** The volume `p` is (or would be) on, with its size and the space this process may still use. */
  def volume(p: Path): Option[Storage.Volume] =
    existing(p).flatMap { e =>
      scala.util.Try {
        val s = Files.getFileStore(e)
        Storage.Volume(s.toString, com.histmersion.api.ByteCount(s.getTotalSpace), com.histmersion.api.ByteCount(s.getUsableSpace))
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
