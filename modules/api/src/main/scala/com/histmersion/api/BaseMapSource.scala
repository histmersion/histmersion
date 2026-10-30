package com.histmersion.api

/** `ui.base-map-source`: which base maps the web page offers (docs/user-guide/how-to/offline.md).
  *
  *   - `local`: only the maps drawn from the server's own extracts (Readable and Physical) — the page then asks no
  *     other host for anything;
  *   - `online`: those AND the online raster maps (`ui.tiles` and its Muted and Dark variants), the online Standard the
  *     default;
  *   - `auto` (the default setting): `local` once the stored extract can draw one of the vector styles whole (Physical
  *     from any download; Readable from a `--keep-all` one), else `online`.
  *
  * `/api/config` answers the resolved value (`local` or `online`), never `auto`.
  */
object BaseMapSource:
  val Auto = "auto"
  val Local = "local"
  val Online = "online"
  val All: List[String] = List(Auto, Local, Online)

  /** The setting as the page uses it: `local` or `online` (`auto` decided from the extract; anything else is `auto`).
    * Pure.
    */
  def resolve(setting: String, status: BasemapStatus): String =
    setting.trim.toLowerCase(java.util.Locale.ROOT) match
      case Local  => Local
      case Online => Online
      case _      =>
        val whole = status.styles.exists(s => BaseStyles.all.exists(_.id == s.id) && s.missing.isEmpty)
        if status.available && whole then Local else Online
