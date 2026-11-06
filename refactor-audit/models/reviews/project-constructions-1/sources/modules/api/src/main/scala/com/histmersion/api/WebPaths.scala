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

  val Places: Set[com.histmersion.api.WebPaths.Places] = Set(_root_.com.histmersion.api.WebPaths.Places("explore"), _root_.com.histmersion.api.WebPaths.Places("library"), _root_.com.histmersion.api.WebPaths.Places("research"), _root_.com.histmersion.api.WebPaths.Places("settings"), _root_.com.histmersion.api.WebPaths.Places("setup"))

  val Items: Set[com.histmersion.api.WebPaths.Items] = Set(_root_.com.histmersion.api.WebPaths.Items("place"), _root_.com.histmersion.api.WebPaths.Items("event"), _root_.com.histmersion.api.WebPaths.Items("run"), _root_.com.histmersion.api.WebPaths.Items("manager"), _root_.com.histmersion.api.WebPaths.Items("job"))

  /** Old addresses that saved links may still use. */
  val Legacy: Set[com.histmersion.api.WebPaths.Legacy] = Set(_root_.com.histmersion.api.WebPaths.Legacy("acquire"), _root_.com.histmersion.api.WebPaths.Legacy("tasks"), _root_.com.histmersion.api.WebPaths.Legacy("log"), _root_.com.histmersion.api.WebPaths.Legacy("merge"), _root_.com.histmersion.api.WebPaths.Legacy("about"), _root_.com.histmersion.api.WebPaths.Legacy("storage"))

  def isApp(segments: List[com.histmersion.api.WebPaths.IsAppSegments]): com.histmersion.api.WebPaths.IsAppResult = segments match
    case Nil                            => _root_.com.histmersion.api.WebPaths.IsAppResult(true)
    case first :: _ if Places(first)    => _root_.com.histmersion.api.WebPaths.IsAppResult(true)
    case List(first, _) if Items(first) => _root_.com.histmersion.api.WebPaths.IsAppResult(true)
    case List(first) if Legacy(first)   => _root_.com.histmersion.api.WebPaths.IsAppResult(true)
    case _                              => _root_.com.histmersion.api.WebPaths.IsAppResult(false)
