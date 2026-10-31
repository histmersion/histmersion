package com.histmersion.util.storage

/** Why a store operation did not happen. `message` is what callers show; it names the path where the operation failed,
  * as the file system said.
  */
enum StoreError(val message: String):
  case NotFound(where: String) extends StoreError(s"$where: no such file")
  case NotAFile(where: String) extends StoreError(s"$where is not a file")
  case Failed(reason: String) extends StoreError(reason)

  /** An in-memory store has no file to hand to another program. */
  case NotOnDisk(where: String) extends StoreError(s"$where is not on disk")
