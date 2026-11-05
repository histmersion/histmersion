package com.histmersion.upstreams.wikidata
final case class WikidataItem(id: WikidataItem.Id, statements: List[WikidataStatement], commonsCategory: Option[WikidataItem.CommonsCategory])

object WikidataItem:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonsCategory = CommonsCategory.T
  object CommonsCategory extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
