package com.histmersion
package web

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.scalajs.js

import org.scalajs.dom

import sttp.client4.*
import com.histmersion.lint.{tolerated, LintRule}

/** The placeholders of a tile URL template ([[ApiClient.tileTemplate]]). Pure. */
object TilePlaceholder:
  import com.histmersion.api.catalogue.TilesApi.TileQuery

  val Z: TileQuery.Z = TileQuery.Z(900000001)
  val X: TileQuery.X = TileQuery.X(900000002L)
  val Y: TileQuery.Y = TileQuery.Y(900000003L)

  /** `uri` with the placeholder tile replaced by `{z}/{x}/{y}`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fill(uri: String): String = uri.replace(s"/$Z/$X/$Y", "/{z}/{x}/{y}")
