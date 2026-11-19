package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** A collection's search: the entries shown, and how many matched in all (an `unlinked` listing shows a page of them).
  */
final case class CollectionSearch(
    entries: List[CollectionEntry],
    total: CollectionSearch.Total
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionSearch:
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
