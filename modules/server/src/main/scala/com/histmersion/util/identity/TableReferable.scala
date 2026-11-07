package com.histmersion.util.identity

import com.histmersion.domain.{Described, ItemKey, ItemKind, ItemRef, Referable}
import com.histmersion.util.database.Transactor

/** A [[Referable]] over the owner's tables: each kind's [[RecordTable]], read on a connection of its own. */
abstract class TableReferable(db: Transactor, tables: List[RecordTable]) extends Referable:
  private val byKind = tables.map(t => t.kind -> t).toMap

  val kinds: Set[ItemKind] = byKind.keySet

  def resolve(kind: ItemKind, key: ItemKey): Either[com.histmersion.util.identity.TableReferable.Problem, ItemRef] =
    byKind.get(kind).toRight(s"no records of kind '${kind.value}'").flatMap(t => db.read(t.resolve(key)))

  def describe(refs: Set[ItemRef]): Map[ItemRef, Described] =
    val asked = refs.filter(r => byKind.contains(r.kind))
    if asked.isEmpty then Map.empty
    else db.read(tables.flatMap(t => t.describe(asked)).toMap)

object TableReferable:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
