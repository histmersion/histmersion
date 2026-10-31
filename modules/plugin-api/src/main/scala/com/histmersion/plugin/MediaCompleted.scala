package com.histmersion.plugin

/** Items completed in one batch ([[MediaCollection.completeAll]]): each by its key (one that could not be completed is
  * left out, and stored as it was found), and what went wrong, one line each.
  */
final case class MediaCompleted(items: Map[String, MediaItem], failed: List[String] = Nil)
