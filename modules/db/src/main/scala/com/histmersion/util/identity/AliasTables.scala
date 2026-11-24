package com.histmersion
package util.identity

import javax.sql.DataSource
import com.histmersion.api.aliases.AliasKind
import com.histmersion.lint.{LintRule, tolerated}

/** Every alias table the owners registered, by kind: how an old identifier of any kind is followed. */
final class AliasTables(val tables: List[AliasTable]):
  private val byKind: Map[AliasKind, AliasTable] = tables.map(t => t.kind -> t).toMap

  def get(kind: AliasKind): Option[AliasTable] = byKind.get(kind)

  /** The identifier a record of `kind` answers to now; `id` itself for a kind nobody registered. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def current(ds: DataSource, kind: AliasKind, id: AliasTable.Id): AliasTable.Id =
    get(kind).fold(AliasTable.Id(id.value.trim))(_.current(ds, id))

  /** The identifier an old one of `kind` was renamed to, when it is one. */
  def renamedTo(ds: DataSource, kind: AliasKind, id: AliasTable.Id): Option[AliasTable.Id] =
    get(kind).flatMap(_.renamedTo(ds, id))
