package com.histmersion.util.storage

/** A store's file as the supporting areas hand it to another program (a child process's directory or log), and a file
  * the HTTP layer hands in (an upload, kept where the server put it).
  */
type HandedFile = java.io.File

object HandedFile:
  /** A handed-in file's bytes. */
  final case class Snapshot(name: String, size: Long, bytes: ByteSource)

  def snapshot(file: HandedFile): Snapshot = Snapshot(file.getName, file.length(), source(file))
  def source(file: HandedFile): ByteSource = ByteSource.file(file.toPath)
