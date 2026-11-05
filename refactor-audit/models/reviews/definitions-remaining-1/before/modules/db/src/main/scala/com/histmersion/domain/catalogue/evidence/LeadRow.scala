package com.histmersion.domain.catalogue.evidence

import com.augustnagro.magnum.DbCodec

/** The facts one event's lead is computed from: its dates, tier and size, the findings near it, the sources proposed
  * and confirmed.
  */
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
