package com.histmersion.util.storage

import com.histmersion.{ByteCount, ByteOffset}

/** A file held open for positional reads by many threads at once. A reader on disk is an interruptible channel: a
  * thread interrupted while it reads closes it for every thread, so a channel closed that way is opened again — the
  * interrupted read fails, every other one goes on.
  */
trait RandomReader extends AutoCloseable:
  def where: com.histmersion.util.storage.RandomReader.WhereResult
  def size: ByteCount

  /** `length` bytes from `offset`; fails when the file ends before them. */
  def read(offset: ByteOffset, length: com.histmersion.util.storage.RandomReader.ReadLength): Array[Byte]

  /** Up to `length` bytes from `offset`, fewer when the file ends first. */
  def readUpTo(offset: ByteOffset, length: com.histmersion.util.storage.RandomReader.ReadUpToLength): Array[Byte]

object RandomReader:
  type WhereResult = WhereResult.T
  object WhereResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadLength = ReadLength.T
  object ReadLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadUpToLength = ReadUpToLength.T
  object ReadUpToLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
