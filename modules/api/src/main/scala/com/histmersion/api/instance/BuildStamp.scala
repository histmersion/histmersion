package com.histmersion
package api.instance
import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** `licence` and `sourceUrl` are `None` from an older server. */
final case class BuildStamp(
    version: BuildStamp.Version,
    gitCommit: Option[BuildStamp.GitCommit] = None,
    commitDate: Option[BuildStamp.CommitDate] = None,
    dirty: BuildStamp.Dirty = BuildStamp.Dirty(false),
    licence: Option[BuildStamp.Licence] = None,
    sourceUrl: Option[BuildStamp.SourceUrl] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object BuildStamp:
  type Version = Version.T
  object Version extends Newtype[String, Pure]
  type GitCommit = GitCommit.T
  object GitCommit extends Newtype[String, Pure]
  type CommitDate = CommitDate.T
  object CommitDate extends Newtype[String, Pure]
  type Dirty = Dirty.T
  object Dirty extends Newtype[Boolean, Pure]
  type Licence = Licence.T
  object Licence extends Newtype[String, Pure]
  type SourceUrl = SourceUrl.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object SourceUrl extends Newtype[String, Pure]
