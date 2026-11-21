package com.histmersion
package server.instance

import com.histmersion.api.instance.BuildId
import com.histmersion.server.Static
import com.histmersion.util.storage.Resources

/** The web bundle this server ships, named by its build (G109, review finding W6). The build writes the bundle's id
  * into the bundle and beside it (`web/build-id.txt`); the server hands the same id to the page three ways: in the
  * bundle's address (a new build is a new address, so no browser runs a cached old bundle against it), in
  * `/api/config`, and in a header of every answer. The page compares them with its own and asks for a reload when they
  * differ, instead of failing later on an answer it cannot read.
  */
final case class WebBundle(id: BuildId):
  import WebBundle.Token

  /** The bundle's address, the build in its query. */
  def path: String = s"/web/app.bundle.js?v=${id.encoded}"

  /** The web UI's page shell (`web/index.html`) with its [[Token]]s replaced by the build: the bundle's preload and
    * `boot.js`, which loads the bundle with its own query.
    */
  def shell(index: Static.Asset): Static.Asset = index.filled(Token, id.encoded)

  /** The header every answer carries. */
  def header: (String, String) = BuildId.Header -> id.encoded

object WebBundle:
  /** Written by build.sbt beside `web/app.bundle.js`: the id it wrote into the bundle. */
  val IdResource: Resources.ResourceName = Resources.ResourceName("web/build-id.txt")

  /** What `web/index.html` writes where the build goes. */
  val Token = "{{build}}"

  /** The bundle on this class path; an empty id when it was built without one (a class path without the linked bundle)
    * or the file names none: the page then compares nothing.
    */
  def read(resources: Resources): WebBundle =
    WebBundle(resources.text(IdResource).toOption.flatMap(t => BuildId.parse(t.trimmed)).getOrElse(BuildId("")))
