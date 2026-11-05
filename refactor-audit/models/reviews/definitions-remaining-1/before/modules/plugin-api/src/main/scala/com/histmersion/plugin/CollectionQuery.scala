package com.histmersion.plugin

/** A search of a [[SourceCollection]].
  *
  * @param text
  *   the words to search for, as the collection's own search reads them; `None` lists the whole collection (an empty
  *   text is a search for nothing in particular, which a collection may answer with everything)
  * @param limit
  *   the most items to answer; `None` leaves the bound to the collection (the whole of a local library)
  */
final case class CollectionQuery(text: Option[String], limit: Option[Int] = None)
