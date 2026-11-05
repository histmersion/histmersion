package com.histmersion.plugin.testkit

import com.histmersion.plugin.testkit.internal.RecordedExchange

import java.nio.file.{Files, Path}

/** Paths of a test's recordings. */
object Recordings:
  /** Whether `dir` holds at least one recorded exchange. */
  type ExistResult = ExistResult.T
  object ExistResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def exist(dir: Path): com.histmersion.plugin.testkit.Recordings.ExistResult =
    Files.isDirectory(dir) && {
      val files = Files.list(dir)
      try files.anyMatch(_.getFileName.toString.endsWith(RecordedExchange.Suffix))
      finally files.close()
    }
