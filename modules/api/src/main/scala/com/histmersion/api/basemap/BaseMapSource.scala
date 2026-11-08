package com.histmersion
package api.basemap
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
object BaseMapSource:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]
  type All = AllValue
  type AllValue = AllValue.T
  object AllValue extends RefinedType[String, Pure]

  val Auto = "auto"
  val Local = "local"
  val Online = "online"
  val All: List[All] = List(AllValue(Auto), AllValue(Local), AllValue(Online))

  def resolve(setting: Wire, status: Basemap.Status): Wire =
    (setting).value.trim.toLowerCase(java.util.Locale.ROOT) match
      case Local  => Wire(Local)
      case Online => Wire(Online)
      case _      =>
        val whole = status.styles.exists(s => BaseStyles.all.exists(_.id == s.id) && s.missing.isEmpty)
        if ((status.available) == Basemap.Status.Available(true)) && whole then Wire(Local) else Wire(Online)
