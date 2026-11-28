package com.histmersion
package upstreams.wikidata

import io.github.iltotore.iron.constraint.any.Pure
final case class WikidataItem(
    id: WikidataItem.Id,
    statements: List[WikidataStatement],
    commonsCategory: Option[WikidataItem.CommonsCategory]
)

object WikidataItem:
  export plugin.WikidataId as Id
  type CommonsCategory = CommonsCategory.T
  object CommonsCategory extends Newtype[String, Pure]
