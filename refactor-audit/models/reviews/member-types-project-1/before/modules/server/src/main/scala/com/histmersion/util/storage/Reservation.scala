package com.histmersion.util.storage

import com.histmersion.{ByteCount, ByteOffset}

import java.io.OutputStream

/** A file being written beside its key: written in full (a stream, positional writes), then committed — moved over the
  * key in one step — or discarded. Nothing reads a half-written file.
  */
trait Reservation extends AutoCloseable:
  def key: Store.Key

  /** Where the file being written is, for messages. */
  def where: String

  /** The file's content from the start, replacing what was written. */
  def output(): OutputStream

  /** Overwrite `bytes` at `offset` in what was written. */
  def writeAt(offset: ByteOffset, bytes: Array[Byte]): Unit

  def size: ByteCount

  /** Move the file over the key in one step, replacing what is there; with `keepPermissions`, the replaced file's
    * permissions carry over.
    */
  def commit(keepPermissions: Boolean = false): Either[Store.Error, Stored]

  /** Remove the file; nothing happens to the key. */
  def discard(): Unit

  /** Discards unless committed. */
  def close(): Unit

  /** The file itself, for a program the supporting areas hand it to (a dump, a settings check). */
  private[util] def onDisk: Either[Store.Error, java.nio.file.Path]
