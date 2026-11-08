package com.histmersion
package domain.personal

import io.github.iltotore.iron.constraint.any.Pure

/** The words a search over marks asks for, lower-cased, each to be found (case does not matter). */
final case class SearchWords(words: List[SearchWords.Words]):
  def isEmpty: Boolean = words.isEmpty

  def matches(texts: String*): Boolean =
    val all = texts.mkString(" ").toLowerCase
    words.forall(w => all.contains(w.value))

object SearchWords:
  type Query = Query.T
  object Query extends Newtype[String, Pure]

  type Words = Words.T
  object Words extends Newtype[String, Pure]

  val Everything: SearchWords = SearchWords(Nil)

  def of(q: Option[Query]): SearchWords =
    SearchWords(q.toList.flatMap(_.value.toLowerCase.split("\\s+").toList).map(_.trim).filter(_.nonEmpty).map(Words(_)))
