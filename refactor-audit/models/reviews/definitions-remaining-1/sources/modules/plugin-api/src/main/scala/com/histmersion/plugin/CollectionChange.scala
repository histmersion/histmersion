package com.histmersion.plugin

/** One field a collection changes (or would change) in an item: its name in the collection, what it holds now and what
  * it is written to.
  */
final case class CollectionChange(field: CollectionChange.Field, from: CollectionChange.From, to: CollectionChange.To)

object CollectionChange:
  type Field = Field.T
  object Field extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type From = From.T
  object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type To = To.T
  object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
