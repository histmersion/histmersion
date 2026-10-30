package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*

final case class SiteTile(
    id: String,
    label: String,
    kind: String,
    siteType: Option[String],
    basis: String,
    periodStart: Option[Long],
    periodEnd: Option[Long]
)
