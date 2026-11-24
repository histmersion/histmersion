package com.histmersion
package api.library
import scala.annotation.tailrec
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The AGPL (section 13) has a server offer everyone who uses it over a network the source of the very version that is
  * running: the repository at the commit it was built from, or the repository itself when the commit is unknown. The
  * `/tree/<commit>` form is the one GitHub, GitLab and Forgejo share.
  */
object CorrespondingSource:
  type Commit = Commit.T
  object Commit extends Newtype[String, Pure]
  type Repository = Repository.T
  object Repository extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def url(repository: Repository, commit: Option[Commit]): Url =
    val repo = (repository).value.trim.stripSuffix("/").stripSuffix(".git")
    commit.map((_).value.trim).filter(_.nonEmpty).fold(Url(repo))(c => Url(s"$repo/tree/$c"))
