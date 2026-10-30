package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*

final case class LeadRow(
    id: String,
    label: String,
    kind: String,
    notBefore: Option[Long],
    beginBy: Option[Long],
    endFrom: Option[Long],
    notAfter: Option[Long],
    tier: String,
    vei: Option[Int],
    magnitude: Option[Double],
    findingsNear: Int,
    proposed: Int,
    attesting: Int
) derives DbCodec
