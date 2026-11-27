package com.histmersion
package web.map.maplibre

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
@js.native
trait LngLatBounds extends js.Object:
  def getWest(): Double = js.native
  def getSouth(): Double = js.native
  def getEast(): Double = js.native
  def getNorth(): Double = js.native
