package com.histmersion
package util.identity
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.domain.{Described, ItemKey, ItemKind, ItemRef, Referable}
import com.histmersion.util.database.Transactor

/** A [[Referable]] over the owner's tables: each kind's [[RecordTable]], read on a connection of its own. */
abstract class TableReferable(db: Transactor, tables: List[RecordTable]) extends Referable:
  private val byKind = tables.map(t => t.kind -> t).toMap

  val kinds: Set[ItemKind] = byKind.keySet

  def resolve(kind: ItemKind, key: ItemKey): Either[Problem, ItemRef] =
    byKind
      .get(kind)
      .toRight(Problem.Unclassified(Legacy.Text(s"no records of kind '${kind.value}'")))
      .flatMap(t => db.read(t.resolve(key)))

  def describe(refs: Set[ItemRef]): Map[ItemRef, Described] =
    val asked = refs.filter(r => byKind.contains(r.kind))
    if asked.isEmpty then Map.empty
    else db.read(tables.flatMap(t => t.describe(asked)).toMap)
