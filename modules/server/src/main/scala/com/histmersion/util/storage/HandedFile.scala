package com.histmersion
package util.storage

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A store's file as the supporting areas hand it to another program (a child process's directory or log), and a file
  * the HTTP layer hands in (an upload, kept where the server put it).
  */
type HandedFile = java.io.File

object HandedFile:
  /** A handed-in file's bytes. */
  final case class Snapshot(name: Snapshot.Name, size: Snapshot.Size, bytes: ByteSource)

  object Snapshot:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Size = Size.T
    object Size extends Newtype[Long, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def snapshot(file: HandedFile): Snapshot =
    Snapshot(Snapshot.Name(file.getName), Snapshot.Size(file.length()), source(file))
  def source(file: HandedFile): ByteSource = ByteSource.file(file.toPath)
