package com.histmersion.api.basemap
import com.histmersion.api.given
object BaseMapSource:
  val Auto = "auto"
  val Local = "local"
  val Online = "online"
  val All: List[String] = List(Auto, Local, Online)

  def resolve(setting: String, status: Basemap.Status): String =
    setting.trim.toLowerCase(java.util.Locale.ROOT) match
      case Local  => Local
      case Online => Online
      case _      =>
        val whole = status.styles.exists(s => BaseStyles.all.exists(_.id == s.id) && s.missing.isEmpty)
        if status.available && whole then Local else Online
