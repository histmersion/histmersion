package com.histmersion.api

object WebPaths:

  val Places: Set[String] = Set("explore", "library", "research", "settings", "setup")

  val Items: Set[String] = Set("place", "event", "run", "manager", "job")

  /** Old addresses that saved links may still use. */
  val Legacy: Set[String] = Set("acquire", "tasks", "log", "merge", "about", "storage")

  def isApp(segments: List[String]): Boolean = segments match
    case Nil                            => true
    case first :: _ if Places(first)    => true
    case List(first, _) if Items(first) => true
    case List(first) if Legacy(first)   => true
    case _                              => false
