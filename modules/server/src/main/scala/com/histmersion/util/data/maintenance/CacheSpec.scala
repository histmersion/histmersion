package com.histmersion.util.data.maintenance

import com.histmersion.util.data.DataOwner

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
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
