package com.histmersion
package util.storage

import java.io.{BufferedInputStream, ByteArrayInputStream, InputStream}
import java.nio.file.{Files, Path}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Bytes that can be read from their start as often as needed, and their length: a bundle on disk, a file a store
  * keeps, an upload. The bundle code reads one through this, never a path.
  */
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
  object Content extends RefinedType[Array[Byte], Pure]



  def file(path: Path): ByteSource = new ByteSource:
    def size: ByteCount = ByteCount.assume(Files.size(path))
    def open(): InputStream = Files.newInputStream(path)

  def bytes(data: Content): ByteSource = new ByteSource:
    def size: ByteCount = ByteCount.assume(data.value.length.toLong)
    def open(): InputStream = ByteArrayInputStream(data.value)
