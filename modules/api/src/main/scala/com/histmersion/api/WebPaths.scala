package com.histmersion.api

object WebPaths:
  type Segment = Segment.T
  object Segment extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Places = PlacesValue
  type Items = ItemsValue
  type Legacy = LegacyValue

  val Places: Set[com.histmersion.api.WebPaths.Places] = Set(_root_.com.histmersion.api.WebPaths.Segment("explore"), _root_.com.histmersion.api.WebPaths.Segment("library"), _root_.com.histmersion.api.WebPaths.Segment("research"), _root_.com.histmersion.api.WebPaths.Segment("settings"), _root_.com.histmersion.api.WebPaths.Segment("setup"))

  val Items: Set[com.histmersion.api.WebPaths.Items] = Set(_root_.com.histmersion.api.WebPaths.Segment("place"), _root_.com.histmersion.api.WebPaths.Segment("event"), _root_.com.histmersion.api.WebPaths.Segment("run"), _root_.com.histmersion.api.WebPaths.Segment("manager"), _root_.com.histmersion.api.WebPaths.Segment("job"))

  /** Old addresses that saved links may still use. */
  val Legacy: Set[com.histmersion.api.WebPaths.Legacy] = Set(_root_.com.histmersion.api.WebPaths.Segment("acquire"), _root_.com.histmersion.api.WebPaths.Segment("tasks"), _root_.com.histmersion.api.WebPaths.Segment("log"), _root_.com.histmersion.api.WebPaths.Segment("merge"), _root_.com.histmersion.api.WebPaths.Segment("about"), _root_.com.histmersion.api.WebPaths.Segment("storage"))

  def isApp(segments: List[com.histmersion.api.WebPaths.Segment]): Boolean = segments match
    case Nil                            => (true)
    case first :: _ if Places(_root_.com.histmersion.api.WebPaths.Segment((first).value))    => (true)
    case List(first, _) if Items(_root_.com.histmersion.api.WebPaths.Segment((first).value)) => (true)
    case List(first) if Legacy(_root_.com.histmersion.api.WebPaths.Segment((first).value))   => (true)
    case _                              => (false)
