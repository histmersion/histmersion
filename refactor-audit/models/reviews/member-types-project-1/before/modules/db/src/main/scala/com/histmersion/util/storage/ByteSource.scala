package com.histmersion.util.storage

import java.io.{BufferedInputStream, ByteArrayInputStream, InputStream}
import java.nio.file.{Files, Path}

/** Bytes that can be read from their start as often as needed, and their length: a bundle on disk, a file a store
  * keeps, an upload. The bundle code reads one through this, never a path.
  */
trait ByteSource:
  def size: Long
  def open(): InputStream

  /** `use` over the bytes, opened and buffered for it and closed after. */
  final def read[A](use: InputStream => A): A =
    val in = BufferedInputStream(open(), 1 << 16)
    try use(in)
    finally in.close()

object ByteSource:
  def file(path: Path): ByteSource = new ByteSource:
    def size: Long = Files.size(path)
    def open(): InputStream = Files.newInputStream(path)

  def bytes(data: Array[Byte]): ByteSource = new ByteSource:
    def size: Long = data.length.toLong
    def open(): InputStream = ByteArrayInputStream(data)
