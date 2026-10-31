package com.histmersion.plugins.media

import com.histmersion.plugin.*

/** A collection that needs a key of the user's own: registered with its key resolved from its secret reference, or
  * none. Without one its import is refused with what to set, and the API is not asked.
  *
  * @param conf
  *   its settings
  * @param plugin
  *   its plugin's id, named in what to set
  * @param key
  *   the key, when one is set
  * @param needs
  *   what key it needs ("an api.data.gov key")
  * @param settings
  *   its settings' path (`media.smithsonian`)
  */
abstract class KeyedCollection(
    conf: CollectionSettings,
    plugin: String,
    key: Option[String],
    needs: String,
    settings: String
) extends MediaCollection:
  override def perPlace: Int = conf.perPlace
  override def requestIntervalMs: Int = conf.requestIntervalMs
  override def refusalBackoffMs: List[Long] = conf.refusalBackoffMs
  override def latestYear: Option[Year] = conf.latestYear.map(Year(_))
  val role = MediaCollection.Object

  /** What `histmersion media providers` says of it: its description, and that it is not asked without a key. */
  protected def described(text: String): String = text + (if key.isEmpty then " — NO KEY SET: not asked" else "")

  override def check: Either[String, Unit] =
    if !conf.enabled then Left(s"$title is switched off ($settings.enabled = false)")
    else
      key
        .toRight(
          s"$title needs $needs: set histmersion.plugins.$plugin.api-key to a secret " +
            "reference (\"env:<NAME>\" or a vault's, e.g. \"keepassxc:<entry>#password\"); it was not asked"
        )
        .map(_ => ())

  /** The key, for a request: a collection without one is never asked ([[check]]). */
  protected def apiKey: String = key.getOrElse("")

  /** Pure: an HTTP 401/403 of a key-gated API named as a refused key, with the setting to check. */
  protected def explain(e: String): String =
    if e.contains("HTTP 401") || e.contains("HTTP 403") then
      s"$e — the API key was refused (check histmersion.plugins.$plugin.api-key)"
    else e
