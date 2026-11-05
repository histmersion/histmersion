package com.histmersion.api.catalogue
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.given

final case class SiteTile(
    id: String,
    label: String,
    kind: String,
    siteType: Option[String],
    basis: String,
    periodStart: Option[Long],
    periodEnd: Option[Long]
)
