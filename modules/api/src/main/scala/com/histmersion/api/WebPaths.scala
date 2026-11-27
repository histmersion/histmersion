package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.ApiUi)
object WebPaths:
  type Segment = Segment.T
  object Segment extends Newtype[String, Pure]

  type Places = PlacesValue
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type PlacesValue = Segment
  val PlacesValue: Segment.type = Segment
  type Items = ItemsValue
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ItemsValue = Segment
  val ItemsValue: Segment.type = Segment
  type SavedLinks = SavedLinkValue
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type SavedLinkValue = Segment
  val SavedLinkValue: Segment.type = Segment

  val Places: Set[Places] =
    Set(Segment("explore"), Segment("library"), Segment("research"), Segment("settings"), Segment("setup"))

  val Items: Set[Items] = Set(Segment("place"), Segment("event"), Segment("run"), Segment("manager"), Segment("job"))

  /** Old addresses that saved links may still use. */
  val SavedLinks: Set[SavedLinks] =
    Set(Segment("acquire"), Segment("tasks"), Segment("log"), Segment("merge"), Segment("about"), Segment("storage"))

  def isApp(segments: List[Segment]): Boolean = segments match
    case Nil                              => (true)
    case first :: _ if Places(first)      => (true)
    case List(first, _) if Items(first)   => (true)
    case List(first) if SavedLinks(first) => (true)
    case _                                => (false)
