package com.histmersion
package api

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Edge
import com.histmersion.api.media.MediaItem
import com.histmersion.api.library.Confidence
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.Source

object Palette:
  object Ramp:
    type Stops = Stops.T
    object Stops extends RefinedType[Int, Pure]
  type CssVar = CssVar.T
  object CssVar extends RefinedType[String, Pure]
  val edgeKinds: List[Edge.Kind.Wire] = List(
    Edge.Kind.Wire("references"),
    Edge.Kind.Wire("challenges"),
    Edge.Kind.Wire("refutes"),
    Edge.Kind.Wire("updates"),
    Edge.Kind.Wire("superseded_by"),
    Edge.Kind.Wire("supersedes"),
    Edge.Kind.Wire("confirms"),
    Edge.Kind.Wire("cites"),
    Edge.Kind.Wire("foundational_for"),
    Edge.Kind.Wire("compilation_of"),
    Edge.Kind.Wire("review_of"),
    com.histmersion.api.library.Edge.Kind.Wire("verifies")
  )
  val statuses: List[com.histmersion.api.library.Source.Status.Wire] =
    List(com.histmersion.api.library.Source.Status.Wire("foundational"), com.histmersion.api.library.Source.Status.Wire("current"), com.histmersion.api.library.Source.Status.Wire("contested"), com.histmersion.api.library.Source.Status.Wire("superseded"), com.histmersion.api.library.Source.Status.Wire("debunked"), com.histmersion.api.library.Source.Status.Wire("retracted"), com.histmersion.api.library.Source.Status.Wire("unassessed"))
  val mediaKinds: List[MediaItem.Kind] = List(MediaItem.Kind("figure"), MediaItem.Kind("photo"), MediaItem.Kind("map"), MediaItem.Kind("plan"), MediaItem.Kind("scan"))
  val confidences: List[Confidence.Wire] = List(Confidence.Wire("high"), Confidence.Wire("medium"), Confidence.Wire("low"), Confidence.Wire("contested"))

  val goalStages: List[Goal.Status.All] = Goal.Status.all

  val rampStops: Ramp.Stops = Ramp.Stops(5)

  private def token(family: String, known: List[String], name: String): String =
    if known.contains(name) then (s"--$family-$name") else (s"--$family-default")

  def edge(kind: Edge.Kind.Wire): CssVar = CssVar(token("edge", edgeKinds.map(_.value), kind.value))
  def status(status: Source.Status.Wire): CssVar = CssVar(token("status", statuses.map(_.value), status.value))

  def goalStage(stage: Goal.Status.All): CssVar = CssVar(s"--stage-${stage.value.trim.toLowerCase}")
  def goalStageSoft(stage: Goal.Status.All): CssVar = CssVar(s"${goalStage(stage).value}-soft")
  def media(kind: MediaItem.Kind): CssVar = CssVar(token("media", mediaKinds.map(_.value), kind.value))
  def confidence(level: Confidence.Wire): CssVar = CssVar(s"--conf-${level.value}")

  def allTokens: List[CssVar] =
    (edgeKinds.map(edge) :+ CssVar("--edge-default")) ++ (statuses.map(status) :+ CssVar("--status-default")) ++
      (mediaKinds.map(media) :+ CssVar("--media-default")) ++ confidences.map(confidence) ++
      (0 until rampStops.value).map(i => CssVar(s"--ramp-$i")) ++ goalStages.flatMap(s => List(goalStage(s), goalStageSoft(s))) ++
      List("--graph-hover", "--graph-label", "--graph-label-hover", "--place-pick", "--place-preview").map(CssVar(_)) ++
      mapLabelTokens

  val mapLabelTokens: List[CssVar] =
    for tone <- List("light", "dark"); part <- List("ink", "halo", "note") yield CssVar(s"--map-label-$part-$tone")
