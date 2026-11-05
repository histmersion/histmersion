package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.nio.file.Path

/** A plugin jar to resolve the dependencies of, with the text of the POM that lists them when it was published with
  * one. Without a POM it has no dependencies of its own.
  */
final case class PluginArtifact(jar: Path, pom: Option[PluginArtifact.Pom]):
  def fileName: com.histmersion.pluginresolver.PluginArtifact.FileNameResult = _root_.com.histmersion.pluginresolver.PluginArtifact.FileNameResult(jar.getFileName.toString)

object PluginArtifact:
  type FileNameResult = FileNameResult.T
  object FileNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Pom = Pom.T
  object Pom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
