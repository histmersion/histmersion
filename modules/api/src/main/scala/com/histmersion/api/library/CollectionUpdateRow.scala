package com.histmersion
package api.library

import com.histmersion.api.given
import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** One item of an update of every linked item: its id, the sources linked to it, its state (`would-change`, `updated`,
  * `failed`, `up-to-date`, `shared`, `missing`, `refused`), the changes and why it failed or was refused.
  */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class CollectionUpdateRow(
    id: CollectionUpdateRow.Id,
    citekeys: List[CollectionUpdateRow.Citekeys],
    state: CollectionUpdateRow.State,
    changes: List[CollectionFieldChange],
    reasons: List[Problem]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionUpdateRow:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Citekeys = Citekeys.T
  object Citekeys extends Newtype[String, Pure]
  type State = State.T
  object State extends Newtype[String, Pure]
