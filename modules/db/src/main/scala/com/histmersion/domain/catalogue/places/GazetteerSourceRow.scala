package com.histmersion.domain.catalogue.places

import com.histmersion.db.*
import com.histmersion.db.given

import scala.annotation.tailrec
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*

/** One row of `gazetteer_sources` (V071). */
final case class GazetteerSourceRow(
    key: String,
    kind: String,
    title: String,
    version: String,
    sha256: String,
    file: String,
    url: Option[String],
    licence: Option[String],
    citation: Option[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    places: Int,
    names: Int,
    links: Int,
    importedAt: String
) derives DbCodec
