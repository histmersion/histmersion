package com.histmersion
package plugins.media

import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure

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
    plugin: KeyedCollection.Plugin,
    key: Option[KeyedCollection.Key],
    needs: KeyedCollection.Needs,
    settings: KeyedCollection.Settings
) extends MediaCollection:
  override def perPlace: MediaCollection.PerPlaceResult = MediaCollection.PerPlaceResult(conf.perPlace.value)
  override def requestIntervalMs: MediaCollection.RequestIntervalMsResult = MediaCollection.RequestIntervalMsResult(conf.requestIntervalMs.value)
  override def refusalBackoffMs: List[MediaCollection.RefusalBackoffMsResult] =
    conf.refusalBackoffMs.map(ms => MediaCollection.RefusalBackoffMsResult(ms.value))
  override def latestYear: Option[Year] = conf.latestYear.map(y => Year(y.value))
  val role = MediaItem.Role(MediaCollection.Object)

  /** What `histmersion media providers` says of it: its description, and that it is not asked without a key. */
  protected def described(text: String): MediaCollection.DescriptionResult =
    MediaCollection.DescriptionResult(text + (if key.isEmpty then " — NO KEY SET: not asked" else ""))

  override def check: Either[MediaCollection.CheckResultPart1, Unit] =
    if !conf.enabled.value then
      Left(MediaCollection.CheckResultPart1(s"${title.value} is switched off (${settings.value}.enabled = false)"))
    else
      key
        .toRight(
          MediaCollection.CheckResultPart1(
            s"${title.value} needs ${needs.value}: set histmersion.plugins.${plugin.value}.api-key to a secret " +
              "reference (\"env:<NAME>\" or a vault's, e.g. \"keepassxc:<entry>#password\"); it was not asked"
          )
        )
        .map(_ => ())

  /** The key, for a request: a collection without one is never asked ([[check]]). */
  protected def apiKey: KeyedCollection.Key = key.getOrElse(KeyedCollection.Key(""))

  /** Pure: an HTTP 401/403 of a key-gated API named as a refused key, with the setting to check. */
  protected def explain(e: KeyedCollection.Problem): KeyedCollection.Explanation =
    if e.value.contains("HTTP 401") || e.value.contains("HTTP 403") then
      KeyedCollection.Explanation(s"${e.value} — the API key was refused (check histmersion.plugins.${plugin.value}.api-key)")
    else KeyedCollection.Explanation(e.value)

object KeyedCollection:
  type Explanation = Explanation.T
  object Explanation extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Plugin = Plugin.T
  object Plugin extends Newtype[String, Pure]
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Needs = Needs.T
  object Needs extends Newtype[String, Pure]
  type Settings = Settings.T
  object Settings extends Newtype[String, Pure]

