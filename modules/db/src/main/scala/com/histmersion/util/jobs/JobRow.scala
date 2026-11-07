package com.histmersion.util.jobs

import com.histmersion.util.database.given

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given

/** Flat projection of `jobs` (positional, like the other write-path rows). */
final private[jobs] case class JobRow(
    id: JobRow.Id,
    kind: JobRow.Kind,
    status: JobRow.Status,
    citekey: Option[JobRow.Citekey],
    progress: Option[JobRow.Progress],
    error: Option[JobRow.Error],
    sha256: Option[JobRow.Sha256],
    createdAt: JobRow.CreatedAt,
    updatedAt: JobRow.UpdatedAt,
    acquireItem: Option[JobRow.AcquireItem],
    detail: Option[JobRow.Detail]
) derives DbCodec

object JobRow:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citekey = com.histmersion.domain.library.Citekey
  val Citekey: _root_.com.histmersion.domain.library.Citekey.type = _root_.com.histmersion.domain.library.Citekey
  type Progress = Progress.T
  object Progress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Error = Error.T
  object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sha256 = Sha256.T
  object Sha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AcquireItem = AcquireItem.T
  object AcquireItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Detail = Detail.T
  object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
