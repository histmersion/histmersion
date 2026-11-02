package com.histmersion.api.maintenance
import com.histmersion.api.given
object StorageAdvice:

  def shrink(key: String, keep: Int): Option[String] = key match
    case "backups" =>
      Some(if keep > 0 then s"lower backups.keep (now $keep) — the next backup deletes the older archives"
      else "set backups.keep (now 0 = keep every archive), or delete old archives")
    case "basemap"   => Some("histmersion basemap rm (one layer: histmersion basemap rm --layer <name>)")
    case "gazetteer" => Some("delete the cached extracts — histmersion places load fetches them again")
    case "database"  =>
      Some("histmersion maintenance vacuum; agents.keep-messages-days trims the agents' message traces")
    case "toolchain"     => Some("histmersion-toolchain remove (needed again only to relink plugins)")
    case "logs"          => Some("delete old log files (the server reopens them)")
    case "bundles"       => Some("scratch: safe to empty while no bundle export or import is running")
    case "acquire-spool" => Some("scratch: safe to empty while no acquisition is running")
    case "codex-home"    =>
      Some(
        "lower agents.providers.codex.keep-sessions-days — older run transcripts are deleted and its SQLite " +
          "files vacuumed while no Codex run uses it (every retention-every-hours); never delete auth.json, its login"
      )
    case k if k.startsWith("cache:") => Some("a cache: safe to delete, it is fetched or rebuilt again")
    case _                           => None
