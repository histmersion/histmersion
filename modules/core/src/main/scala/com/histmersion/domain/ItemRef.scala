package com.histmersion
package domain

/** A record of some domain as another one stores a reference to it: its kind, the key it had, and its uid where its
  * kind has one and the record existed when the reference was made.
  */
final case class ItemRef(kind: ItemKind, key: ItemKey, uid: Option[ItemUid])
