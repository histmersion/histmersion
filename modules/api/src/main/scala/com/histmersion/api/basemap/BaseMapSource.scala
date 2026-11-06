package com.histmersion.api.basemap
import com.histmersion.api.given
object BaseMapSource:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type All = AllValue
  type AllValue = AllValue.T
  object AllValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Auto = "auto"
  val Local = "local"
  val Online = "online"
  val All: List[com.histmersion.api.basemap.BaseMapSource.All] = List(_root_.com.histmersion.api.basemap.BaseMapSource.AllValue(Auto), _root_.com.histmersion.api.basemap.BaseMapSource.AllValue(Local), _root_.com.histmersion.api.basemap.BaseMapSource.AllValue(Online))

  def resolve(setting: com.histmersion.api.basemap.BaseMapSource.Wire, status: Basemap.Status): com.histmersion.api.basemap.BaseMapSource.Wire =
    (setting).value.trim.toLowerCase(java.util.Locale.ROOT) match
      case Local  => _root_.com.histmersion.api.basemap.BaseMapSource.Wire(Local)
      case Online => _root_.com.histmersion.api.basemap.BaseMapSource.Wire(Online)
      case _      =>
        val whole = status.styles.exists(s => BaseStyles.all.exists(_.id == s.id) && s.missing.isEmpty)
        if ((status.available) == _root_.com.histmersion.api.basemap.Basemap.Status.Available(true)) && whole then _root_.com.histmersion.api.basemap.BaseMapSource.Wire(Local) else _root_.com.histmersion.api.basemap.BaseMapSource.Wire(Online)
