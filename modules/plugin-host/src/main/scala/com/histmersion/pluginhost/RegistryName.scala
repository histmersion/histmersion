package com.histmersion
package pluginhost

import io.github.iltotore.iron.constraint.string.Match

/** A name a plugin's contribution is registered under, and a plugin's id: kebab-case, so it is a key of the settings
  * and the provider half of `provider:model`.
  */
type RegistryName = RegistryName.T
object RegistryName extends Newtype[String, Match["[a-z0-9][a-z0-9-]*"]]
