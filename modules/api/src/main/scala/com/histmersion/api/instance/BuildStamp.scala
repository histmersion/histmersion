package com.histmersion
package api.instance
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `licence` and `sourceUrl` are `None` from an older server. */
final case class BuildStamp(
    version: BuildStamp.Version,
    gitCommit: Option[BuildStamp.GitCommit] = None,
    commitDate: Option[BuildStamp.CommitDate] = None,
    dirty: BuildStamp.Dirty = BuildStamp.Dirty(false),
    licence: Option[BuildStamp.Licence] = None,
    sourceUrl: Option[BuildStamp.SourceUrl] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object BuildStamp:
  type Version = Version.T
  object Version extends RefinedType[String, Pure]
  type GitCommit = GitCommit.T
  object GitCommit extends RefinedType[String, Pure]
  type CommitDate = CommitDate.T
  object CommitDate extends RefinedType[String, Pure]
  type Dirty = Dirty.T
  object Dirty extends RefinedType[Boolean, Pure]
  type Licence = Licence.T
  object Licence extends RefinedType[String, Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends RefinedType[String, Pure]
