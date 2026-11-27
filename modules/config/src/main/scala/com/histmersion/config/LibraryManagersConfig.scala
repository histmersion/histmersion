package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.lint.{movesTo, FeaturePlace}

/** The library's settings among `agents.managers.*`: a research area's watcher. */
@movesTo(FeaturePlace.LibraryWorkflows)
final case class LibraryManagersConfig(watch: WatchManagerConfig) derives ConfigReader
