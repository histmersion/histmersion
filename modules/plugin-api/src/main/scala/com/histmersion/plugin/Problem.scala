package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** Why an SPI operation failed, as a sentence for a person: the one failure message of the plugin SPI. Never contains
  * a secret's value.
  */
type Problem = Problem.T

object Problem extends Newtype[String, Pure]
