package com.histmersion.plugin

import scala.compiletime.deferred

/** The host's HTTP client, as a plugin sees it. Failures are `Left(message)`, never exceptions. */
trait PluginHttp:
  def get(url: String, headers: Map[String, String] = Map.empty): Either[String, Array[Byte]]
