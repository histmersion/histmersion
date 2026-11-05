package com.histmersion.web.library

/** A shelf of My library: everything the filters select, the person's bookmarks, their notes, what they have not marked
  * read, and what they opened lately.
  */
enum Shelf(val wire: String, val label: String):
  case Everything extends Shelf("all", "Everything")
  case Bookmarks extends Shelf("bookmarks", "Bookmarks")
  case Notes extends Shelf("notes", "My notes")
  case Unread extends Shelf("unread", "Not read yet")
  case Recent extends Shelf("recent", "Recently opened")

object Shelf:
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.web.library.Shelf.ParseS): Option[Shelf] = values.find(_.wire == s.trim.toLowerCase)
