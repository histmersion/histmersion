package com.histmersion.api

/** The addresses the web UI's own page answers, for the server to serve it the app rather than a 404:
  * the four places and everything under them, the pages of single items, and the old addresses of the
  * lists, which the page sends on to their new ones. `/source/…` and `/article/…` are the server's own
  * standalone pages and are not among them. Pure. */
object WebPaths:

  /** First path segments under which every address is a page of the web UI. */
  val Places: Set[String] = Set("explore", "library", "research", "settings")

  /** First segments of an item's page: one segment more, the item's id (`/run/<id>`). */
  val Items: Set[String] = Set("place", "event", "run", "manager", "job")

  /** The old one-segment addresses of the lists and of the merge screen. */
  val Legacy: Set[String] = Set("acquire", "tasks", "log", "merge")

  /** Is this path (its segments, empty ones dropped) a page of the web UI? */
  def isApp(segments: List[String]): Boolean = segments match
    case Nil                              => true
    case first :: _ if Places(first)      => true
    case List(first, _) if Items(first)   => true
    case List(first) if Legacy(first)     => true
    case _                                => false
