package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A plugin's id: its settings key (`histmersion.plugins.<id>`), kebab-case. */
type PluginId = PluginId.T

object PluginId extends RefinedType[String, Match["[a-z0-9][a-z0-9-]*"]]
