package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
    arxiv: Option[WorkKey.Arxiv] = None,
    pmid: Option[WorkKey.Pmid] = None,
    pmcid: Option[WorkKey.Pmcid] = None,
    archive: Option[WorkKey.Archive] = None,
    isbn: Option[WorkKey.Isbn] = None,
    title: Option[WorkKey.Title] = None,
    authors: List[WorkKey.Authors] = Nil,
    year: Option[Year] = None,
    format: Option[WorkKey.Format] = None
):
  /** No identifier at all (title, authors, year and format alone identify nothing to fetch). */
  def isEmpty: WorkKey.IsEmptyResult = WorkKey.IsEmptyResult(List(doi, arxiv, pmid, pmcid, archive, isbn).forall(_.isEmpty))

object WorkKey:
  type IsEmptyResult = IsEmptyResult.T
  object IsEmptyResult extends RefinedType[Boolean, Pure]

  type Arxiv = Arxiv.T
  object Arxiv extends RefinedType[String, Pure]
  type Pmid = Pmid.T
  object Pmid extends RefinedType[String, Pure]
  type Pmcid = Pmcid.T
  object Pmcid extends RefinedType[String, Pure]
  type Archive = Archive.T
  object Archive extends RefinedType[String, Pure]
  type Isbn = Isbn.T
  object Isbn extends RefinedType[String, Pure]
  type Title = Title.T
  object Title extends RefinedType[String, Pure]
  type Authors = Authors.T
  object Authors extends RefinedType[String, Pure]
  type Format = Format.T
  object Format extends RefinedType[String, Pure]
