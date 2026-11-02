package com.histmersion.domain.library.acquisition

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate

/** The forward-citation and same-author alerts of a goal's sources (OpenAlex): the works citing one, a work's authors,
  * and an author's newer works.
  */
trait CitationAlerts:
  def citing(doi: String, since: LocalDate, limit: Int): Either[String, List[WorkRef]]
  def authors(doi: String): Either[String, List[String]]
  def byAuthor(authorId: String, since: LocalDate, limit: Int): Either[String, List[WorkRef]]
