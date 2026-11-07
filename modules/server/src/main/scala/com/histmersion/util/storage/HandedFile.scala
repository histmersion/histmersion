package com.histmersion.util.storage

/** A store's file as the supporting areas hand it to another program (a child process's directory or log), and a file
  * the HTTP layer hands in (an upload, kept where the server put it).
  */
type HandedFile = java.io.File

object HandedFile:
  /** A handed-in file's bytes. */
  final case class Snapshot(name: Snapshot.Name, size: Snapshot.Size, bytes: ByteSource)

  object Snapshot:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Size = Size.T
    object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def snapshot(file: HandedFile): Snapshot = Snapshot(Snapshot.Name(file.getName), Snapshot.Size(file.length()), source(file))
  def source(file: HandedFile): ByteSource = ByteSource.file(file.toPath)
