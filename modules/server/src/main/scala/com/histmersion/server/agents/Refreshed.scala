package com.histmersion.server.agents

import com.histmersion.api.automation.PlanQuota.*
import io.circe.{parser, Json}

import scala.jdk.CollectionConverters.*

/** What one refresh did: how (`probe`, `app-server`), whether a reading came back, and what. */
final case class Refreshed(mode: Refreshed.Mode, ok: Refreshed.Ok, detail: Refreshed.Detail):
  def text: String = s"$mode: $detail"

object Refreshed:
  type Mode = Mode.T
  object Mode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ok = Ok.T
  object Ok extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Detail = Detail.T
  object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
