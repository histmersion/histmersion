package com.histmersion.api.library
import scala.annotation.tailrec
import com.histmersion.api.given

/** The AGPL (section 13) has a server offer everyone who uses it over a network the source of the very version that is
  * running: the repository at the commit it was built from, or the repository itself when the commit is unknown. The
  * `/tree/<commit>` form is the one GitHub, GitLab and Forgejo share.
  */
object CorrespondingSource:
  type UrlResult = UrlResult.T
  object UrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlRepository = UrlRepository.T
  object UrlRepository extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UrlCommit = UrlCommit.T
  object UrlCommit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def url(repository: com.histmersion.api.library.CorrespondingSource.UrlRepository, commit: Option[com.histmersion.api.library.CorrespondingSource.UrlCommit]): com.histmersion.api.library.CorrespondingSource.UrlResult =
    val repo = repository.trim.stripSuffix("/").stripSuffix(".git")
    commit.map(_.trim).filter(_.nonEmpty).fold(repo)(c => s"$repo/tree/$c")
