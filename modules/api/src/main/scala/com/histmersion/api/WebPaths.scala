package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure

object WebPaths:
  type Segment = Segment.T
  object Segment extends Newtype[String, Pure]

  type Places = PlacesValue
  type PlacesValue = Segment
  val PlacesValue: Segment.type = Segment
  type Items = ItemsValue
  type ItemsValue = Segment
  val ItemsValue: Segment.type = Segment
  type SavedLinks = SavedLinkValue
  type SavedLinkValue = Segment
  val SavedLinkValue: Segment.type = Segment

  val Places: Set[Places] =
    Set(Segment("explore"), Segment("library"), Segment("research"), Segment("settings"), Segment("setup"))

  val Items: Set[Items] = Set(Segment("place"), Segment("event"), Segment("run"), Segment("manager"), Segment("job"))

  /** Old addresses that saved links may still use. */
  val SavedLinks: Set[SavedLinks] =
    Set(Segment("acquire"), Segment("tasks"), Segment("log"), Segment("merge"), Segment("about"), Segment("storage"))

  def isApp(segments: List[Segment]): Boolean = segments match
    case Nil                                             => (true)
    case first :: _ if Places(Segment((first).value))    => (true)
    case List(first, _) if Items(Segment((first).value)) => (true)
    case List(first) if SavedLinks(Segment((first).value))   => (true)
    case _                                               => (false)
