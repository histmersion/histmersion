package com.histmersion
package pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.nio.file.Path
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A plugin jar to resolve the dependencies of, with the text of the POM that lists them when it was published with
  * one. Without a POM it has no dependencies of its own.
  */
final case class PluginArtifact(jar: Path, pom: Option[PluginArtifact.Pom]):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fileName: Resolver.FileName = Resolver.FileName(jar.getFileName.toString)

object PluginArtifact:
  type Pom = Pom.T
  object Pom extends Newtype[String, Pure]
