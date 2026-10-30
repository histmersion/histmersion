package com.histmersion.server.agents

import com.histmersion.api.PlanQuota.*
import io.circe.{parser, Json}

import scala.jdk.CollectionConverters.*

/** What one refresh did: how (`probe`, `app-server`), whether a reading came back, and what. */
final case class Refreshed(mode: String, ok: Boolean, detail: String):
  def text: String = s"$mode: $detail"
