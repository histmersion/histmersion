package com.histmersion.plugins.media

import com.histmersion.plugin.MediaCollection
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.plugin.Secret
import hearth.kindlings.sconfigderivation.ConfigReader

/** One of the collections that need a key of the user's own: OFF by default, its key a secret reference in its block
  * (`api-key`). Registered without a key, the collection is listed and its import says what is missing; it is never
  * asked.
  */
abstract class KeyedPlugin(id: KeyedPlugin.Id, name: KeyedPlugin.Name) extends MediaPlugin(id.value):
  type Config = KeyedPlugin.Config

  /** The collection over the media settings and the key, when one is set. */
  protected def collection(c: KeyedPlugin.Config, key: Option[com.histmersion.plugins.media.KeyedCollection.Key], ctx: Plugin.Context): MediaCollection

  def register(c: KeyedPlugin.Config, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    c.apiKey
      .fold[Either[Plugin.RegisterResultPart1, Option[KeyedCollection.Key]]](Right(None))(s =>
        ctx
          .secret(s)
          .left
          .map(e => Plugin.RegisterResultPart1(e.value))
          .map(v => Option(v).map(_.value.trim).filter(_.nonEmpty).map(KeyedCollection.Key(_)))
      )
      .flatMap(key =>
        ctx.mediaCollections
          .add(com.histmersion.plugin.Registry.AddId(name.value), collection(c, key, ctx))
          .left
          .map(e => Plugin.RegisterResultPart1(e.value))
      )

object KeyedPlugin:
  /** A key-gated collection's settings: `api-key`, the key as a SECRET REFERENCE (`env:<NAME>`, a vault's), never the
    * key; `null` = none, and the collection is then not asked. Its other settings are Histmersion's own `media.*`.
    */
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Config(apiKey: Option[Secret], media: MediaSettings) derives ConfigReader
