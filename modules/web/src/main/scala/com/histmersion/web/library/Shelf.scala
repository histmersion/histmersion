package com.histmersion.web.library

/** A shelf of My library: everything the filters select, the person's bookmarks, their notes, what they have
  * not marked read, and what they opened lately. */
enum Shelf(val wire: String, val label: String):
  case Everything extends Shelf("all", "Everything")
  case Bookmarks  extends Shelf("bookmarks", "Bookmarks")
  case Notes      extends Shelf("notes", "My notes")
  case Unread     extends Shelf("unread", "Not read yet")
  case Recent     extends Shelf("recent", "Recently opened")

object Shelf:
  def parse(s: String): Option[Shelf] = values.find(_.wire == s.trim.toLowerCase)
