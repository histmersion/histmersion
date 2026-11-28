package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Source
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class ListedSource(citekey: Citekey, title: ListedSource.Title, year: Option[ListedSource.Year], status: Source.Status) derives JsonCodec

object ListedSource:
  type Year = Year.T
  object Year extends Newtype[Int, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
