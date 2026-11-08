package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** Why an operation of the plugin SPI failed, as a sentence for a person: the SPI's one failure message. It never
  * contains a secret's value.
  */
type Problem = Problem.T

object Problem extends Newtype[String, Pure]
