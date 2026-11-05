package com.histmersion.api.library
import scala.annotation.tailrec
import com.histmersion.api.given

/** The AGPL (section 13) has a server offer everyone who uses it over a network the source of the very version that is
  * running: the repository at the commit it was built from, or the repository itself when the commit is unknown. The
  * `/tree/<commit>` form is the one GitHub, GitLab and Forgejo share.
  */
object CorrespondingSource:
  def url(repository: String, commit: Option[String]): String =
    val repo = repository.trim.stripSuffix("/").stripSuffix(".git")
    commit.map(_.trim).filter(_.nonEmpty).fold(repo)(c => s"$repo/tree/$c")
