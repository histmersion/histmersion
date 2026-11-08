package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** An environment variable, by name and value: one of the host's own ([[Plugin.Context.env]]) or one passed to a
  * process ([[Processes.run]], [[Processes.start]]).
  */
object EnvVar:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Value = Value.T
  object Value extends Newtype[String, Pure]
