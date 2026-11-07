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
    plugin: KeyedCollection.Plugin,
    key: Option[KeyedCollection.Key],
    needs: KeyedCollection.Needs,
    settings: KeyedCollection.Settings
) extends MediaCollection:
  override def perPlace: Int = conf.perPlace
  override def requestIntervalMs: Int = conf.requestIntervalMs
  override def refusalBackoffMs: List[Long] = conf.refusalBackoffMs
  override def latestYear: Option[Year] = conf.latestYear.map(Year(_))
  val role = MediaCollection.Object

  /** What `histmersion media providers` says of it: its description, and that it is not asked without a key. */
  protected def described(text: com.histmersion.plugins.media.KeyedCollection.Text): com.histmersion.plugins.media.KeyedCollection.DescribedResult = text + (if key.isEmpty then " — NO KEY SET: not asked" else "")

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
  protected def apiKey: com.histmersion.plugins.media.KeyedCollection.Key = key.getOrElse("")

  /** Pure: an HTTP 401/403 of a key-gated API named as a refused key, with the setting to check. */
  protected def explain(e: com.histmersion.plugins.media.KeyedCollection.Problem): com.histmersion.plugins.media.KeyedCollection.Explanation =
    if e.contains("HTTP 401") || e.contains("HTTP 403") then
      s"$e — the API key was refused (check histmersion.plugins.$plugin.api-key)"
    else e

object KeyedCollection:
  type Explanation = Explanation.T
  object Explanation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Plugin = Plugin.T
  object Plugin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Needs = Needs.T
  object Needs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Settings = Settings.T
  object Settings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type DescribedResult = DescribedResult.T
  object DescribedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
