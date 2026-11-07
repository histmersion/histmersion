package com.histmersion.web.library

import com.histmersion.api.library.Sort

/** How Library's table of sources is ordered — its own choice, kept in the address (`lsort=`), apart from Explore's:
  * the reader names an order of publication here, not a ranking of the map's results.
  */
enum LibrarySort(val wire: LibrarySort.Wire, val label: LibrarySort.Label, val sort: Sort):
  case Relevance extends LibrarySort(com.histmersion.web.library.LibrarySort.Wire("relevance"), com.histmersion.web.library.LibrarySort.Label("Relevance"), Sort.Relevance)
  case Newest extends LibrarySort(com.histmersion.web.library.LibrarySort.Wire("newest"), com.histmersion.web.library.LibrarySort.Label("Newest publication"), Sort.Year)
  case Oldest extends LibrarySort(com.histmersion.web.library.LibrarySort.Wire("oldest"), com.histmersion.web.library.LibrarySort.Label("Oldest publication"), Sort.Oldest)
  case Title extends LibrarySort(com.histmersion.web.library.LibrarySort.Wire("title"), com.histmersion.web.library.LibrarySort.Label("Title"), Sort.Title)
  case PeriodStart extends LibrarySort(com.histmersion.web.library.LibrarySort.Wire("period"), com.histmersion.web.library.LibrarySort.Label("Period start"), Sort.PeriodStart)

object LibrarySort:
  /** Pure: the order an address names; relevance for none, or for one it does not know. */
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def of(wire: Option[com.histmersion.web.library.LibrarySort.Wire]): LibrarySort =
    wire.flatMap(w => values.find(_.wire == w.trim.toLowerCase)).getOrElse(Relevance)
