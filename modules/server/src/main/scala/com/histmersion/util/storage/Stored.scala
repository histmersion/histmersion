package com.histmersion.util.storage

import com.histmersion.ByteCount

import java.time.Instant

/** A name in a store as it is now: a file (its size) or a directory (size zero), and when it last changed. */
final case class Stored(key: Store.Key, kind: Stored.Kind, size: ByteCount, modified: Instant):
  def isFile: com.histmersion.util.storage.Stored.IsFileResult = kind == Stored.Kind.File
  def isDirectory: com.histmersion.util.storage.Stored.IsDirectoryResult = kind == Stored.Kind.Directory

object Stored:

  /** What a name in a store holds. */
  type IsFileResult = IsFileResult.T
  object IsFileResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsDirectoryResult = IsDirectoryResult.T
  object IsDirectoryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Kind:
    case File, Directory
