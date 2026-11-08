package com.histmersion
package web.library

import com.histmersion.api.library.Sort
import io.github.iltotore.iron.constraint.any.Pure

/** How Library's table of sources is ordered — its own choice, kept in the address (`lsort=`), apart from Explore's:
  * the reader names an order of publication here, not a ranking of the map's results.
  */
enum LibrarySort(val wire: LibrarySort.Wire, val label: LibrarySort.Label, val sort: Sort) derives CanEqual:
  case Relevance extends LibrarySort(LibrarySort.Wire("relevance"), LibrarySort.Label("Relevance"), Sort.Relevance)
  case Newest extends LibrarySort(LibrarySort.Wire("newest"), LibrarySort.Label("Newest publication"), Sort.Year)
  case Oldest extends LibrarySort(LibrarySort.Wire("oldest"), LibrarySort.Label("Oldest publication"), Sort.Oldest)
  case Title extends LibrarySort(LibrarySort.Wire("title"), LibrarySort.Label("Title"), Sort.Title)
  case PeriodStart extends LibrarySort(LibrarySort.Wire("period"), LibrarySort.Label("Period start"), Sort.PeriodStart)

object LibrarySort:
  /** Pure: the order an address names; relevance for none, or for one it does not know. */
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]


  def of(wire: Option[Wire]): LibrarySort =
    wire.flatMap(w => values.find(_.wire.value == w.value.trim.toLowerCase)).getOrElse(Relevance)
