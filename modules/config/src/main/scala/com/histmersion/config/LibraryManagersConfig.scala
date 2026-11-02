package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The library's settings among `agents.managers.*`: a research area's watcher. */
final case class LibraryManagersConfig(watch: WatchManagerConfig) derives ConfigReader
