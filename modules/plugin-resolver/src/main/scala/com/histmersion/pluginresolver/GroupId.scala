package com.histmersion
package pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** A Maven group id: `org.scala-lang.modules`. */
type GroupId = GroupId.T
object GroupId extends RefinedType[String, MavenNameRule]
