package com.histmersion.plugin

/** Items completed in one batch ([[MediaCollection.completeAll]]): each by its key (one that could not be completed is
  * left out, and stored as it was found), and what went wrong, one line each.
  */
final case class MediaCompleted(items: Map[MediaCompleted.ItemsKey, MediaItem], failed: List[MediaCompleted.Failed] = Nil)

object MediaCompleted:
  type ItemsKey = ItemsKey.T
  object ItemsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Failed = Failed.T
  object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
