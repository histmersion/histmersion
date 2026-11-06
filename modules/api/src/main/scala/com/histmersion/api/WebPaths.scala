package com.histmersion.api

object WebPaths:

  type Places = PlacesValue
  type PlacesValue = PlacesValue.T
  object PlacesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Items = ItemsValue
  type ItemsValue = ItemsValue.T
  object ItemsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Legacy = LegacyValue
  type LegacyValue = LegacyValue.T
  object LegacyValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAppResult = IsAppResult.T
  object IsAppResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAppSegments = IsAppSegments.T
  object IsAppSegments extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Places: Set[com.histmersion.api.WebPaths.Places] = Set(_root_.com.histmersion.api.WebPaths.PlacesValue("explore"), _root_.com.histmersion.api.WebPaths.PlacesValue("library"), _root_.com.histmersion.api.WebPaths.PlacesValue("research"), _root_.com.histmersion.api.WebPaths.PlacesValue("settings"), _root_.com.histmersion.api.WebPaths.PlacesValue("setup"))

  val Items: Set[com.histmersion.api.WebPaths.Items] = Set(_root_.com.histmersion.api.WebPaths.ItemsValue("place"), _root_.com.histmersion.api.WebPaths.ItemsValue("event"), _root_.com.histmersion.api.WebPaths.ItemsValue("run"), _root_.com.histmersion.api.WebPaths.ItemsValue("manager"), _root_.com.histmersion.api.WebPaths.ItemsValue("job"))

  /** Old addresses that saved links may still use. */
  val Legacy: Set[com.histmersion.api.WebPaths.Legacy] = Set(_root_.com.histmersion.api.WebPaths.LegacyValue("acquire"), _root_.com.histmersion.api.WebPaths.LegacyValue("tasks"), _root_.com.histmersion.api.WebPaths.LegacyValue("log"), _root_.com.histmersion.api.WebPaths.LegacyValue("merge"), _root_.com.histmersion.api.WebPaths.LegacyValue("about"), _root_.com.histmersion.api.WebPaths.LegacyValue("storage"))

  def isApp(segments: List[com.histmersion.api.WebPaths.IsAppSegments]): com.histmersion.api.WebPaths.IsAppResult = segments match
    case Nil                            => _root_.com.histmersion.api.WebPaths.IsAppResult(true)
    case first :: _ if Places(_root_.com.histmersion.api.WebPaths.PlacesValue((first).value))    => _root_.com.histmersion.api.WebPaths.IsAppResult(true)
    case List(first, _) if Items(_root_.com.histmersion.api.WebPaths.ItemsValue((first).value)) => _root_.com.histmersion.api.WebPaths.IsAppResult(true)
    case List(first) if Legacy(_root_.com.histmersion.api.WebPaths.LegacyValue((first).value))   => _root_.com.histmersion.api.WebPaths.IsAppResult(true)
    case _                              => _root_.com.histmersion.api.WebPaths.IsAppResult(false)
