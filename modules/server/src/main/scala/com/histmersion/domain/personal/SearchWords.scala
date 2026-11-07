package com.histmersion.domain.personal

/** The words a search over marks asks for, lower-cased, each to be found (case does not matter). */
final case class SearchWords(words: List[SearchWords.Words]):
  def isEmpty: Boolean = words.isEmpty

  def matches(texts: String*): Boolean =
    val all = texts.mkString(" ").toLowerCase
    words.forall(w => all.contains(w.value))

object SearchWords:
  type Query = Query.T
  object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Words = Words.T
  object Words extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Everything: SearchWords = SearchWords(Nil)

  def of(q: Option[com.histmersion.domain.personal.SearchWords.Query]): SearchWords =
    SearchWords(q.toList.flatMap(_.value.toLowerCase.split("\\s+").toList).map(_.trim).filter(_.nonEmpty).map(Words(_)))
