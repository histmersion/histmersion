package com.histmersion.plugin

/** One thing an audit found wrong with a linked item: a short code (`Missing`, `Title`, …) and a sentence for a person.
  */
final case class CollectionIssue(code: CollectionIssue.Code, label: CollectionIssue.Label)

object CollectionIssue:
  type Code = Code.T
  object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
