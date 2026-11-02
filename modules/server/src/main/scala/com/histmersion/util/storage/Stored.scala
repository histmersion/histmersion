package com.histmersion.util.storage

import com.histmersion.ByteCount

import java.time.Instant

/** A name in a store as it is now: a file (its size) or a directory (size zero), and when it last changed. */
final case class Stored(key: StoreKey, kind: StoredKind, size: ByteCount, modified: Instant):
  def isFile: Boolean = kind == StoredKind.File
  def isDirectory: Boolean = kind == StoredKind.Directory
