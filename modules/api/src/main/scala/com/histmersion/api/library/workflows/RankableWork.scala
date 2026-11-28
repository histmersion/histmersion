package com.histmersion
package api.library.workflows

import com.histmersion.api.catalogue.Year
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Source
import com.histmersion.api.library.Title
import com.histmersion.api.library.WorkRef
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class RankableWork(
    doi: Doi,
    title: Title,
    year: Option[Year],
    authors: List[AuthorName],
    venue: Option[Source.Venue],
    isOa: WorkRef.IsOa,
    query: RankableWork.Query,
    abstractText: Option[WorkRef.AbstractText]
) derives JsonCodec

object RankableWork:
  type Query = Query.T
  object Query extends Newtype[String, Pure]
