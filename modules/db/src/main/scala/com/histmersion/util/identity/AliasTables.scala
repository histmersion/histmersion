package com.histmersion.util.identity

import javax.sql.DataSource
import com.histmersion.api.aliases.AliasKind

/** Every alias table the owners registered, by kind: how an old identifier of any kind is followed. */
final class AliasTables(val tables: List[AliasTable]):
  private val byKind: Map[AliasKind, AliasTable] = tables.map(t => t.kind -> t).toMap

  def get(kind: AliasKind): Option[AliasTable] = byKind.get(kind)

  /** The identifier a record of `kind` answers to now; `id` itself for a kind nobody registered. */
  def current(ds: DataSource, kind: AliasKind, id: com.histmersion.util.identity.AliasTables.CurrentId): com.histmersion.util.identity.AliasTables.CurrentResult = get(kind).fold(id.trim)(_.current(ds, id))

  /** The identifier an old one of `kind` was renamed to, when it is one. */
  def renamedTo(ds: DataSource, kind: AliasKind, id: com.histmersion.util.identity.AliasTables.RenamedToId): Option[com.histmersion.util.identity.AliasTables.RenamedToResult] = get(kind).flatMap(_.renamedTo(ds, id))

object AliasTables:
  type CurrentResult = CurrentResult.T
  object CurrentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentId = CurrentId.T
  object CurrentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamedToResult = RenamedToResult.T
  object RenamedToResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamedToId = RenamedToId.T
  object RenamedToId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
