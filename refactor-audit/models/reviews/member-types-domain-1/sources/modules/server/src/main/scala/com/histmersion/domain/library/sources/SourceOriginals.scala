package com.histmersion.domain.library.sources

/** Where a source's stored original files are (the uploads and the acquisition spool, the ingest's stores): those a
  * delete removes, and their removal after the delete committed, best effort.
  */
trait SourceOriginals:
  def find(plan: SourceDeletePlan): List[com.histmersion.domain.library.sources.SourceOriginals.FindResult]

  /** The files that could not be removed, each with why. */
  def remove(files: List[com.histmersion.domain.library.sources.SourceOriginals.RemoveFiles]): List[com.histmersion.domain.library.sources.SourceOriginals.RemoveResult]

object SourceOriginals:
  type FindResult = FindResult.T
  object FindResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveResult = RemoveResult.T
  object RemoveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveFiles = RemoveFiles.T
  object RemoveFiles extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
