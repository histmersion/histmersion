package com.histmersion
package upstreams.wikidata

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
final case class WikidataItem(id: WikidataItem.Id, statements: List[WikidataStatement], commonsCategory: Option[WikidataItem.CommonsCategory])

object WikidataItem:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type CommonsCategory = CommonsCategory.T
  object CommonsCategory extends RefinedType[String, Pure]
