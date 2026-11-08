package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** A search of a [[SourceCollection]].
  *
  * @param text
  *   the words to search for, as the collection's own search reads them; `None` lists the whole collection (an empty
  *   text is a search for nothing in particular, which a collection may answer with everything)
  * @param limit
  *   the most items to answer; `None` leaves the bound to the collection (the whole of a local library)
  */
final case class CollectionQuery(text: Option[CollectionQuery.Text], limit: Option[CollectionQuery.Limit] = None)

object CollectionQuery:
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
