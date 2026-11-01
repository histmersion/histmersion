package com.histmersion.domain.catalogue.evidence.passages

/** The sources whose text attests a data point, as the catalogue's evidence reads them: which source a citekey names
  * now (an old citekey resolves to it), and the passages of its text. The library implements it.
  */
trait PassageSource:
  /** The citekey the source named `citekey` has now; `None` when no source has it. */
  def canonical(citekey: String): Option[String]

  /** The sources whose text names `name` as a phrase, those naming it most often first, at most `limit`. */
  def naming(name: String, limit: Int): List[String]

  /** A reader of one source's text, read once however many searches are made in it. */
  def reader(citekey: String): PassageReader
