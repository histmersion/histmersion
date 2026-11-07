package com.histmersion.domain.library.acquisition

import com.histmersion.config.LoadedConfig
import com.histmersion.pluginhost.FetchChain
import com.histmersion.util.plugins.Plugins

/** The acquisition chain over the first-party fetchers (archive.org, Unpaywall, CORE, OpenAlex, PMC, arXiv, landing
  * pages, PMC's JATS text) — discovered and registered through the plugin host exactly as the server registers them,
  * with `loaded`'s settings, in `acquire.chain` order, over `http`. What a test or a one-off caller runs; the server
  * runs the chain of its own plugin generation, which may hold more fetchers or fewer.
  */
object OaFetch:
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Md5 = Md5.T
  object Md5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(loaded: LoadedConfig, http: Http, core: CoreBudget): FetchChain =
    val g = Plugins.fetchers(
      loaded,
      () => HttpPlugin(http),
      CoreBudget.quotas(core, CoreBudget.limits(loaded.config.acquire.core))
    )
    val cfg = loaded.config.acquire
    FetchChain.of(g.registries.sourceFetchers, cfg.chainIds, cfg.allowUnauthorised, plugins = g.registered)._1

  /** Hex MD5 of a download, to compare with the one a PMC dataset record or a Zenodo record states; `None` when the
    * runtime offers no MD5 (the check is then skipped, never failed).
    */
  def md5(b: com.histmersion.domain.library.acquisition.OaFetch.Bytes): Option[com.histmersion.domain.library.acquisition.OaFetch.Md5] =
    scala.util.Try(java.security.MessageDigest.getInstance("MD5").digest(b).map("%02x".format(_)).mkString).toOption
