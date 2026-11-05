package com.histmersion.web.library

import com.histmersion.api.library.Sort

/** How Library's table of sources is ordered — its own choice, kept in the address (`lsort=`), apart from Explore's:
  * the reader names an order of publication here, not a ranking of the map's results.
  */
enum LibrarySort(val wire: String, val label: String, val sort: Sort):
  case Relevance extends LibrarySort("relevance", "Relevance", Sort.Relevance)
  case Newest extends LibrarySort("newest", "Newest publication", Sort.Year)
  case Oldest extends LibrarySort("oldest", "Oldest publication", Sort.Oldest)
  case Title extends LibrarySort("title", "Title", Sort.Title)
  case PeriodStart extends LibrarySort("period", "Period start", Sort.PeriodStart)

object LibrarySort:
  /** Pure: the order an address names; relevance for none, or for one it does not know. */
  type OfWire = OfWire.T
  object OfWire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(wire: Option[com.histmersion.web.library.LibrarySort.OfWire]): LibrarySort =
    wire.flatMap(w => values.find(_.wire == w.trim.toLowerCase)).getOrElse(Relevance)
