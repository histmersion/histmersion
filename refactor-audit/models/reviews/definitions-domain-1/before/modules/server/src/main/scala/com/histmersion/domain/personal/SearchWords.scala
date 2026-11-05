package com.histmersion.domain.personal

/** The words a search over marks asks for, lower-cased, each to be found (case does not matter). */
final case class SearchWords(words: List[String]):
  def isEmpty: Boolean = words.isEmpty

  def matches(texts: String*): Boolean =
    val all = texts.mkString(" ").toLowerCase
    words.forall(all.contains)

object SearchWords:
  val Everything: SearchWords = SearchWords(Nil)

  def of(q: Option[String]): SearchWords =
    SearchWords(q.toList.flatMap(_.toLowerCase.split("\\s+").toList).map(_.trim).filter(_.nonEmpty))
