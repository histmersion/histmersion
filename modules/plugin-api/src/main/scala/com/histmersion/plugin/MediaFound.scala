package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** What a collection with its own targets' pictures ([[MediaMatchRule.Own]]) found for a batch of targets.
  *
  * @param uses
  *   each picture of each target, in the collection's order
  * @param asked
  *   the targets it asked about (the ones it could not ask — no Wikidata item, say — left out); the host counts them
  *   and records each as asked, but those in `unanswered`
  * @param unanswered
  *   the targets whose answer did not come (a failed request): not recorded as asked, so the next import asks again
  * @param read
  *   how many records it read (items, place records), for the report
  * @param failed
  *   what went wrong, one line each, for the report
  * @param notTaken
  *   the links it found but cannot take, each as its reason ("on Flickr (its API needs a key)"), counted in the report
  * @param categories
  *   a place's category at the provider, as (place id, category): recorded as the place's link
  */
final case class MediaFound(
    uses: List[MediaUse],
    asked: List[MediaTarget.Id],
    unanswered: Set[MediaTarget.Id] = Set.empty,
    read: MediaFound.Read = MediaFound.Read(0),
    failed: List[MediaFound.Failed] = Nil,
    notTaken: List[MediaFound.NotTaken] = Nil,
    categories: List[(MediaFound.Category.Target, MediaFound.Category.Name)] = Nil
)

object MediaFound:
  type Read = Read.T
  object Read extends Newtype[Int, Pure]
  type Failed = Failed.T
  object Failed extends Newtype[String, Pure]
  type NotTaken = NotTaken.T
  object NotTaken extends Newtype[String, Pure]

  object Category:
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type Name = Name.T
    object Name extends Newtype[String, Pure]
