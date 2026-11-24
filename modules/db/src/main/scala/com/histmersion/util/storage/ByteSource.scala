package com.histmersion
package util.storage

import java.io.{BufferedInputStream, ByteArrayInputStream, InputStream}
import java.nio.file.{Files, Path}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** Bytes that can be read from their start as often as needed, and their length: a bundle on disk, a file a store
  * keeps, an upload. The bundle code reads one through this, never a path.
  */
@atBoundary(BoundaryKind.Process, "opens bytes (a bundle, a stored file, an upload) as a stream with a length")
trait ByteSource:
  def size: ByteCount
  def open(): InputStream

  /** `use` over the bytes, opened and buffered for it and closed after. */
  final def read[A](use: InputStream => A): A =
    val in = BufferedInputStream(open(), 1 << 16)
    try use(in)
    finally in.close()

object ByteSource:
  type Content = Content.T
  object Content extends Newtype[Array[Byte], Pure]

  def file(path: Path): ByteSource = new ByteSource:
    def size: ByteCount = ByteCount.assume(Files.size(path))
    def open(): InputStream = Files.newInputStream(path)

  def bytes(data: Content): ByteSource = new ByteSource:
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def size: ByteCount = ByteCount.assume(data.value.length.toLong)
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def open(): InputStream = ByteArrayInputStream(data.value)
