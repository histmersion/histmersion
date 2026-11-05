package com.histmersion.domain.personal

/** The words a search over marks asks for, lower-cased, each to be found (case does not matter). */
final case class SearchWords(words: List[SearchWords.Words]):
  def isEmpty: com.histmersion.domain.personal.SearchWords.IsEmptyResult = words.isEmpty

  def matches(texts: String*): com.histmersion.domain.personal.SearchWords.MatchesResult =
    val all = texts.mkString(" ").toLowerCase
    words.forall(all.contains)

object SearchWords:
  type IsEmptyResult = IsEmptyResult.T
  object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchesResult = MatchesResult.T
  object MatchesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfQ = OfQ.T
  object OfQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Words = Words.T
  object Words extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Everything: SearchWords = SearchWords(Nil)

  def of(q: Option[com.histmersion.domain.personal.SearchWords.OfQ]): SearchWords =
    SearchWords(q.toList.flatMap(_.toLowerCase.split("\\s+").toList).map(_.trim).filter(_.nonEmpty))
