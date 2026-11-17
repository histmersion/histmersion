package com.histmersion
package util.jobs
import com.histmersion.util.problem.Problem
import com.histmersion.util.nonempty.NonEmptyVector

import com.histmersion.util.database.given

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure

/** Flat projection of `jobs` (positional, like the other write-path rows). */
final private[jobs] case class JobRow(
    id: JobRow.Id,
    kind: JobRow.Kind,
    status: JobRow.Status,
    citekey: Option[JobRow.Citekey],
    progress: Option[JobRow.Progress],
    error: Option[NonEmptyVector[Problem]],
    sha256: Option[JobRow.Sha256],
    createdAt: JobRow.CreatedAt,
    updatedAt: JobRow.UpdatedAt,
    acquireItem: Option[JobRow.AcquireItem],
    detail: Option[JobRow.Detail]
) derives DbCodec

object JobRow:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Citekey = com.histmersion.domain.library.Citekey
  val Citekey: com.histmersion.domain.library.Citekey.type = com.histmersion.domain.library.Citekey
  type Progress = Progress.T
  object Progress extends Newtype[String, Pure]
  type Sha256 = Sha256.T
  object Sha256 extends Newtype[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
  type AcquireItem = AcquireItem.T
  object AcquireItem extends Newtype[Int, Pure]
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
