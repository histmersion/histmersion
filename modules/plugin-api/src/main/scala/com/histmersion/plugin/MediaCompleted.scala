package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** Items completed in one batch ([[MediaCollection.completeAll]]): each by its key (one that could not be completed is
  * left out, and stored as it was found), and what went wrong, each with the item it was for.
  */
final case class MediaCompleted(
    items: Map[MediaCompleted.ItemsKey, MediaItem],
    failed: List[MediaFound.Failed] = Nil
)

object MediaCompleted:
  type ItemsKey = MediaItem.Key
  val ItemsKey: MediaItem.Key.type = MediaItem.Key
