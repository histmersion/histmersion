package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.nio.file.Path

/** A plugin jar to resolve the dependencies of, with the text of the POM that lists them when it was published with
  * one. Without a POM it has no dependencies of its own.
  */
final case class PluginArtifact(jar: Path, pom: Option[String]):
  def fileName: String = jar.getFileName.toString
