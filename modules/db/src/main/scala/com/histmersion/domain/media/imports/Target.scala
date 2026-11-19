package com.histmersion
package domain.media.imports

import io.github.iltotore.iron.constraint.any.Pure

/** A place or event to ask a provider about: its Wikidata item (empty when the import needs none) and its name (for the
  * report).
  */
final case class Target(targetType: Target.TargetType, id: Target.Id, qid: Target.Qid, label: Target.Label)

object Target:
  export domain.media.assets.PictureTarget.Wire as TargetType
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Qid = Qid.T
  object Qid extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
