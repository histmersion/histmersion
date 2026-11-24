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

/** A source collection the server can run: its name, title and description, what it lets the server do (`read`, `add`,
  * `update`), and whether the server can reach it now (`problem` says what is missing when it cannot).
  */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionInfo(
    name: CollectionInfo.Name,
    title: Title,
    description: CollectionInfo.Description,
    capabilities: List[CollectionInfo.Capabilities],
    reachable: CollectionInfo.Reachable,
    problems: List[Problem]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionInfo:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type Capabilities = Capabilities.T
  object Capabilities extends Newtype[String, Pure]
  type Reachable = Reachable.T
  object Reachable extends Newtype[Boolean, Pure]
