package com.histmersion
package domain
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.lint.{tolerated, LintRule}

/** Every domain's [[Referable]], by the kinds each owns: what a domain that points at records of any kind is given. */
final class Referables(all: List[Referable]):
  private val byKind: Map[ItemKind, Referable] = all.flatMap(r => r.kinds.toList.map(_ -> r)).toMap

  def kinds: Set[ItemKind] = byKind.keySet

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def resolve(kind: ItemKind, key: ItemKey): Either[Problem, ItemRef] =
    byKind
      .get(kind)
      .toRight(Problem.Unclassified(Legacy.Text(s"no records of kind '${kind.value}'")))
      .flatMap(_.resolve(kind, key))

  /** Each kind's records described by its owner, one call per owner. */
  def describe(refs: Set[ItemRef]): Map[ItemRef, Described] =
    refs
      .groupBy(r => byKind.get(r.kind))
      .toList
      .flatMap {
        case (Some(owner), mine) => owner.describe(mine)
        case (None, _)           => Nil
      }
      .toMap
