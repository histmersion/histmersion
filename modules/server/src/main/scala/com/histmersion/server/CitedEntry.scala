package com.histmersion
package server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.Source
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** One line of an article's "Sources cited" footer; `number` is its citation number in the prose (first appearance),
  * `None` for a source cited only outside it (e.g. under "Not used").
  */
@movesTo(FeaturePlace.ResearchArticles)
final case class CitedEntry(
    number: Option[CitedEntry.Number],
    citekey: CitedEntry.Citekey,
    doi: Option[CitedEntry.Doi],
    authors: List[CitedEntry.Authors],
    year: Option[CitedEntry.Year],
    title: Option[CitedEntry.Title],
    status: Option[Source.Status]
)

object CitedEntry:
  type Number = Number.T
  object Number extends Newtype[Int, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Citekey = library.Citekey
  val Citekey: library.Citekey.type = library.Citekey
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Doi = library.Doi
  val Doi: library.Doi.type = library.Doi
  type Authors = Authors.T
  object Authors extends Newtype[String, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  type Year = com.histmersion.Year
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  val Year: com.histmersion.Year.type = com.histmersion.Year
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Title = domain.Title
  val Title: domain.Title.type = domain.Title
