package com.histmersion.util.storage

import java.io.{BufferedInputStream, ByteArrayInputStream, InputStream}
import java.nio.file.{Files, Path}

/** Bytes that can be read from their start as often as needed, and their length: a bundle on disk, a file a store
  * keeps, an upload. The bundle code reads one through this, never a path.
  */
trait ByteSource:
  def size: com.histmersion.util.storage.ByteSource.SizeResult
  def open(): InputStream

  /** `use` over the bytes, opened and buffered for it and closed after. */
  final def read[A](use: InputStream => A): A =
    val in = BufferedInputStream(open(), 1 << 16)
    try use(in)
    finally in.close()

object ByteSource:
  type BytesData = BytesData.T
  object BytesData extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SizeResult = SizeResult.T
  object SizeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def file(path: Path): ByteSource = new ByteSource:
    def size: Long = Files.size(path)
    def open(): InputStream = Files.newInputStream(path)

  def bytes(data: com.histmersion.util.storage.ByteSource.BytesData): ByteSource = new ByteSource:
    def size: Long = data.length.toLong
    def open(): InputStream = ByteArrayInputStream(data)
