package com.histmersion.util.storage

import com.histmersion.{ByteCount, ByteOffset}

/** A file held open for positional reads by many threads at once. A reader on disk is an interruptible channel: a
  * thread interrupted while it reads closes it for every thread, so a channel closed that way is opened again — the
  * interrupted read fails, every other one goes on.
  */
trait RandomReader extends AutoCloseable:
  def where: com.histmersion.util.storage.Blob.Location
  def size: ByteCount

  /** `length` bytes from `offset`; fails when the file ends before them. */
  def read(offset: ByteOffset, length: com.histmersion.util.basemap.PmTiles.ByteLength): com.histmersion.util.storage.ByteSource.Content

  /** Up to `length` bytes from `offset`, fewer when the file ends first. */
  def readUpTo(offset: ByteOffset, length: com.histmersion.util.basemap.PmTiles.ByteLength): com.histmersion.util.storage.ByteSource.Content


