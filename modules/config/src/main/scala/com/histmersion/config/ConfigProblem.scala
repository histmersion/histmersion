package com.histmersion
package config

import io.github.iltotore.iron.constraint.any.Pure

/** Why a settings file or the layers over it cannot be loaded: the message names the file and the setting. */
object ConfigProblem extends Newtype[String, Pure]

type ConfigProblem = ConfigProblem.T
