package com.histmersion
package pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** A Maven artifact id, with the Scala suffix a Scala module carries: `scala-xml_3`. */
type ArtifactId = ArtifactId.T
object ArtifactId extends RefinedType[String, MavenNameRule]
