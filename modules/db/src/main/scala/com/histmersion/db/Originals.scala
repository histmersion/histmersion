package com.histmersion.db

import com.histmersion.util.storage.{ByteSource, ContentDirectory, ContentStore}

import java.io.InputStream
import java.nio.file.Path

/** Where the server keeps the originals it stored at ingest — the converter's spool, `<dir>/<sha256>.<ext>`
  * (`IngestHandlers.spool`): a content-addressed store.
  */
type Originals = ContentStore

object Originals:
  val none: Originals = new ContentStore:
    def find(sha256: String): Option[(ByteSource, String)] = None
    def store(sha256: String, ext: String, in: InputStream): Either[String, Unit] =
      Left("this server stores no originals")

  /** A content-addressed directory: `<dir>/<sha256>.<ext>` — the converter's spool on a server. */
  def inDir(dir: Path): Originals = ContentDirectory(dir)

  /** An extension safe in a file name: short and alphanumeric, else `bin`. Pure. */
  def safeExt(ext: String): String = ContentStore.safeExt(ext)
