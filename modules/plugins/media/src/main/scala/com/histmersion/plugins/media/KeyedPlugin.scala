package com.histmersion
package plugins.media

import com.histmersion.plugin.Problem

import com.histmersion.plugin.MediaCollection
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.plugin.Secret
import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.plugin.Registry
import io.github.iltotore.iron.constraint.any.Pure

/** One of the collections that need a key of the user's own: OFF by default, its key a secret reference in its block
  * (`api-key`). Registered without a key, the collection is listed and its import says what is missing; it is never
  * asked.
  */
abstract class KeyedPlugin(id: KeyedPlugin.Id, name: KeyedPlugin.Name) extends MediaPlugin(id.value):
  type Config = KeyedPlugin.Config

  /** The collection over the media settings and the key, when one is set. */
  protected def collection(
      c: KeyedPlugin.Config,
      key: Option[KeyedCollection.Key],
      ctx: Plugin.Context
  ): MediaCollection

  def register(c: KeyedPlugin.Config, ctx: Plugin.Context): Either[Problem, Unit] =
    c.apiKey
      .fold[Either[Problem, Option[KeyedCollection.Key]]](Right(None))(s =>
        ctx
          .secret(s)
          .left
          .map(e => Problem(e.value))
          .map(v => Option(v).map(_.value.trim).filter(_.nonEmpty).map(KeyedCollection.Key(_)))
      )
      .flatMap(key =>
        ctx.mediaCollections
          .add(Registry.AddId(name.value), collection(c, key, ctx))
          .left
          .map(e => Problem(e.value))
      )

object KeyedPlugin:
  /** A key-gated collection's settings: `api-key`, the key as a SECRET REFERENCE (`env:<NAME>`, a vault's), never the
    * key; `null` = none, and the collection is then not asked. Its other settings are Histmersion's own `media.*`.
    */
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]

  final case class Config(apiKey: Option[Secret], media: MediaSettings) derives ConfigReader
