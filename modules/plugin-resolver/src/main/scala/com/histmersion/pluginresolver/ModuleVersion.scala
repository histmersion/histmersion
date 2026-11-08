package com.histmersion
package pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** A module's version as its POM writes it: `2.4.0`, `0.1.0-SNAPSHOT`. */
type ModuleVersion = ModuleVersion.T
object ModuleVersion extends RefinedType[String, MavenNameRule]
