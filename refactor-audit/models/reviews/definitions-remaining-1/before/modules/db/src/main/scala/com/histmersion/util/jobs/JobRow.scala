package com.histmersion.util.jobs

import com.histmersion.util.database.given

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*

/** Flat projection of `jobs` (positional, like the other write-path rows). */
final private[jobs] case class JobRow(
    id: String,
    kind: String,
    status: String,
    citekey: Option[String],
    progress: Option[String],
    error: Option[String],
    sha256: Option[String],
    createdAt: String,
    updatedAt: String,
    acquireItem: Option[Int],
    detail: Option[String]
) derives DbCodec
