package com.histmersion
package api.catalogue
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import com.histmersion.api.library.Label
import com.histmersion.api.given
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object TileProps:
  val Zoom = "z"
  val Key = "k"
  val Count = "n"
  val MidLo = "lo"
  val MidHi = "hi"
  val West = "w"
  val South = "s"
  val East = "e"
  val North = "nn"

  val Undated = "u"
  val Id = "id"
  val Label = "label"
  val Kind = "kind"
  val SiteType = "siteType"
  val Basis = "basis"
  val PeriodStart = "periodStart"
  val PeriodEnd = "periodEnd"
  val Scale = "scale"
  val Historicity = "historicity"
  val NotBefore = "notBefore"
  val BeginBy = "beginBy"
  val EndFrom = "endFrom"
  val NotAfter = "notAfter"
  val Anchor = "anchor"
  val PlaceId = "placeId"
  val PlaceLabel = "placeLabel"
  val ParentId = "parentId"
  val Tier = "tier"
  val Size = "size"
  val Precision = "precision"
  val Dating = "dating"
  val Citekey = "citekey"
  val AssetName = "assetName"
  val Caption = "caption"
  val Uid = "uid"
  val Provider = "provider"
  val License = "license"
  val Credit = "credit"
  val ThumbUrl = "thumbUrl"
  val SourceUrl = "sourceUrl"
  val LicenseUrl = "licenseUrl"
  val Author = "author"
