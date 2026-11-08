package com.histmersion
package util.data.maintenance

import com.histmersion.util.data.DataOwner
import io.github.iltotore.iron.constraint.any.Pure

/** What an owner keeps derived from its rows: what it is called and shown as, where it is held and what makes it stale.
  */
final case class CacheSpec(
    owner: DataOwner,
    name: CacheName,
    label: CacheSpec.Label,
    holding: CacheHolding,
    staleAfter: List[CacheEvent] = Nil
)

object CacheSpec:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
