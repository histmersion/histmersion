package com.histmersion
package util.storage

import com.histmersion.ByteCount

import java.time.Instant

/** A name in a store as it is now: a file (its size) or a directory (size zero), and when it last changed. */
final case class Stored(key: Store.Key, kind: Stored.Kind, size: ByteCount, modified: Instant):
  def isFile: Boolean = kind == Stored.Kind.File
  def isDirectory: Boolean = kind == Stored.Kind.Directory

object Stored:

  /** What a name in a store holds. */

  enum Kind:
    case File, Directory
