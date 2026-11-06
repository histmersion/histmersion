package com.histmersion.api.library
import scala.annotation.tailrec
import com.histmersion.api.given

/** The AGPL (section 13) has a server offer everyone who uses it over a network the source of the very version that is
  * running: the repository at the commit it was built from, or the repository itself when the commit is unknown. The
  * `/tree/<commit>` form is the one GitHub, GitLab and Forgejo share.
  */
object CorrespondingSource:
  type Commit = Commit.T
  object Commit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Repository = Repository.T
  object Repository extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def url(repository: com.histmersion.api.library.CorrespondingSource.Repository, commit: Option[com.histmersion.api.library.CorrespondingSource.Commit]): com.histmersion.api.library.CorrespondingSource.Url =
    val repo = (repository).value.trim.stripSuffix("/").stripSuffix(".git")
    commit.map((_).value.trim).filter(_.nonEmpty).fold(repo)(c => _root_.com.histmersion.api.library.CorrespondingSource.Url(s"$repo/tree/$c"))
