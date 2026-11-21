package com.histmersion
package server.instance

import com.histmersion.api.instance.BuildId
import com.histmersion.server.Static
import com.histmersion.util.storage.Resources

final case class WebBundle(id: BuildId):
  import WebBundle.Token

  def path: String = s"/web/app.bundle.js?v=${id.encoded}"

  def shell(index: Static.Asset): Static.Asset = index.filled(Token, id.encoded)

  def header: (String, String) = BuildId.Header -> id.encoded

object WebBundle:
  val IdResource: Resources.ResourceName = Resources.ResourceName("web/build-id.txt")

  val Token = "{{build}}"

  def read(resources: Resources): WebBundle =
    WebBundle(resources.text(IdResource).toOption.flatMap(t => BuildId.parse(t.trimmed)).getOrElse(BuildId("")))
