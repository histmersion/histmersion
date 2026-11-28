package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.research.CitedSource
import com.histmersion.api.research.Coverage
import com.histmersion.api.research.CritiquePair
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain
import com.histmersion.domain.research
import com.histmersion.Newtype
import com.histmersion.domain.research.workflows.BodyCorpus

final case class Snapshot(
    slug: Snapshot.Slug,
    title: Snapshot.Title,
    kind: Snapshot.Kind,
    body: Snapshot.Body,
    updatedAt: Option[Snapshot.UpdatedAt],
    truncated: Snapshot.Truncated,
    coverage: Option[Coverage] = None,
    cited: List[CitedSource] = Nil,
    critiques: List[CritiquePair] = Nil
):
  def generated: Option[Article.Coverage.Sections] = coverage.map(Article.Coverage.sections)

object Snapshot:

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Slug = research.Slug
  val Slug: research.Slug.type = research.Slug
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Title = domain.Title
  val Title: domain.Title.type = domain.Title
  export Article.Kind.Wire as Kind
  export Article.Body
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
  type Truncated = Truncated.T
  object Truncated extends Newtype[Boolean, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def requiredSections(
      kind: Kind,
      generated: BodyCorpus.Generated = BodyCorpus.Generated(false)
  ): List[List[String]] =
    if kind.value.trim.equalsIgnoreCase("index") then Nil
    else
      List(List("disagreements"), List("unresolved", "open questions")) ++
        Option.when(!generated.value)(List("not used")).toList
