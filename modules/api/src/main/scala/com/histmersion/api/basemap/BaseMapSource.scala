package com.histmersion
package api.basemap
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
object BaseMapSource:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type All = AllValue
  type AllValue = AllValue.T
  object AllValue extends Newtype[String, Pure]

  val Auto = "auto"
  val Local = "local"
  val Online = "online"
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val All: List[All] = List(AllValue(Auto), AllValue(Local), AllValue(Online))

  /** The "Muted" online base map's tiles (Humanitarian OSM, OSM France): the one tile server besides `ui.tiles` the
    * page may ask, so the server's Content-Security-Policy lists it too.
    */
  val MutedTiles: BasemapApi.TileTemplate =
    BasemapApi.TileTemplate("https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png")

  /** The subdomains a tile template's `{s}` stands for: the page asks each, so the policy lists each. */
  val Subdomains: List[String] = List("a", "b", "c")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def resolve(setting: Wire, status: Basemap.Status): Wire =
    (setting).value.trim.toLowerCase(java.util.Locale.ROOT) match
      case Local  => Wire(Local)
      case Online => Wire(Online)
      case _      =>
        val whole = status.styles.exists(s => BaseStyles.all.exists(_.id == s.id) && s.missing.isEmpty)
        if ((status.available) == Basemap.Status.Available(true)) && whole then Wire(Local) else Wire(Online)
