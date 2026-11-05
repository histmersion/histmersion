package com.histmersion.plugin

/** Every identifier the caller has for one work. A fetcher reads the ones it understands and answers [[Fetch.Miss]]
  * when none fits.
  *
  * @param doi
  *   a DOI, lower-cased, without `https://doi.org/`
  * @param arxiv
  *   an arXiv id (`2101.00001`, `hep-th/9901001`)
  * @param pmid
  *   a PubMed id
  * @param pmcid
  *   a PubMed Central id (`PMC6091220`)
  * @param archive
  *   an archive.org item identifier
  * @param isbn
  *   an ISBN
  * @param title
  *   the work's title, as the caller knows it
  * @param authors
  *   its authors, as the caller knows them
  * @param year
  *   its publication year (signed; BCE negative)
  * @param format
  *   the file format the caller prefers (`pdf`, `epub`) when a work has several files
  */
final case class WorkKey(
    doi: Option[Doi] = None,
    arxiv: Option[String] = None,
    pmid: Option[String] = None,
    pmcid: Option[String] = None,
    archive: Option[String] = None,
    isbn: Option[String] = None,
    title: Option[String] = None,
    authors: List[String] = Nil,
    year: Option[Year] = None,
    format: Option[String] = None
):
  /** No identifier at all (title, authors, year and format alone identify nothing to fetch). */
  def isEmpty: Boolean = List(doi, arxiv, pmid, pmcid, archive, isbn).forall(_.isEmpty)
