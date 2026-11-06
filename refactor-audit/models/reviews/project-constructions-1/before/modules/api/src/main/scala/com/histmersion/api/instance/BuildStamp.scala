package com.histmersion.api.instance
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** `licence` and `sourceUrl` are `None` from an older server. */
final case class BuildStamp(
    version: BuildStamp.Version,
    gitCommit: Option[BuildStamp.GitCommit] = None,
    commitDate: Option[BuildStamp.CommitDate] = None,
    dirty: BuildStamp.Dirty = false,
    licence: Option[BuildStamp.Licence] = None,
    sourceUrl: Option[BuildStamp.SourceUrl] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object BuildStamp:
  type Version = Version.T
  object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GitCommit = GitCommit.T
  object GitCommit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommitDate = CommitDate.T
  object CommitDate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Dirty = Dirty.T
  object Dirty extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Licence = Licence.T
  object Licence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
