package com.histmersion.web.library

/** A shelf of My library: everything the filters select, the person's bookmarks, their notes, what they have not marked
  * read, and what they opened lately.
  */
enum Shelf(val wire: Shelf.Wire, val label: Shelf.Label):
  case Everything extends Shelf("all", "Everything")
  case Bookmarks extends Shelf("bookmarks", "Bookmarks")
  case Notes extends Shelf("notes", "My notes")
  case Unread extends Shelf("unread", "Not read yet")
  case Recent extends Shelf("recent", "Recently opened")

object Shelf:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def parse(s: com.histmersion.web.library.Shelf.Wire): Option[Shelf] = values.find(_.wire == s.trim.toLowerCase)
