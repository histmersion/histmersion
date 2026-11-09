package com.histmersion
package domain.library.sources

import io.github.iltotore.iron.constraint.any.Pure

/** Where a source's stored original files are (the uploads and the acquisition spool, the ingest's stores): those a
  * delete removes, and their removal after the delete committed, best effort.
  */
trait SourceOriginals:
  def find(plan: SourceDeletePlan): List[SourceOriginals.FoundFile]

  /** The files that could not be removed, each with why. */
  def remove(files: List[SourceOriginals.RemoveFiles]): List[SourceOriginals.RemovedFile]

object SourceOriginals:
  type FoundFile = FoundFile.T
  object FoundFile extends Newtype[String, Pure]
  type RemovedFile = RemovedFile.T
  object RemovedFile extends Newtype[String, Pure]
  type RemoveFiles = RemoveFiles.T
  object RemoveFiles extends Newtype[String, Pure]
