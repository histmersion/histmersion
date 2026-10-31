package com.histmersion.plugins.media

import com.histmersion.plugin.{MediaCollection, PluginContext}

/** One of the collections that need a key of the user's own: OFF by default, its key a secret reference in its block
  * (`api-key`). Registered without a key, the collection is listed and its import says what is missing; it is never
  * asked.
  */
abstract class KeyedPlugin(id: String, name: String) extends MediaPlugin(id):
  type Config = KeyedPluginConfig

  /** The collection over the media settings and the key, when one is set. */
  protected def collection(c: KeyedPluginConfig, key: Option[String], ctx: PluginContext): MediaCollection

  def register(c: KeyedPluginConfig, ctx: PluginContext): Either[String, Unit] =
    c.apiKey
      .fold[Either[String, Option[String]]](Right(None))(s =>
        ctx.secret(s).map(v => Option(v).map(_.trim).filter(_.nonEmpty))
      )
      .flatMap(key => ctx.mediaCollections.add(name, collection(c, key, ctx)))
