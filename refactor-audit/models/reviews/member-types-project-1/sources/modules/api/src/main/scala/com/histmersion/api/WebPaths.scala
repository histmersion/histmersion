package com.histmersion.api

object WebPaths:

  type Places = Places.T
  object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Items = Items.T
  object Items extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Legacy = Legacy.T
  object Legacy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAppResult = IsAppResult.T
  object IsAppResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAppSegments = IsAppSegments.T
  object IsAppSegments extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Places: Set[com.histmersion.api.WebPaths.Places] = Set("explore", "library", "research", "settings", "setup")

  val Items: Set[com.histmersion.api.WebPaths.Items] = Set("place", "event", "run", "manager", "job")

  /** Old addresses that saved links may still use. */
  val Legacy: Set[com.histmersion.api.WebPaths.Legacy] = Set("acquire", "tasks", "log", "merge", "about", "storage")

  def isApp(segments: List[com.histmersion.api.WebPaths.IsAppSegments]): com.histmersion.api.WebPaths.IsAppResult = segments match
    case Nil                            => true
    case first :: _ if Places(first)    => true
    case List(first, _) if Items(first) => true
    case List(first) if Legacy(first)   => true
    case _                              => false
