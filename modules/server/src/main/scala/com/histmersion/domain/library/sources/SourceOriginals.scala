package com.histmersion.domain.library.sources

/** Where a source's stored original files are (the uploads and the acquisition spool, the ingest's stores): those a
  * delete removes, and their removal after the delete committed, best effort.
  */
trait SourceOriginals:
  def find(plan: SourceDeletePlan): List[String]

  /** The files that could not be removed, each with why. */
  def remove(files: List[String]): List[String]
