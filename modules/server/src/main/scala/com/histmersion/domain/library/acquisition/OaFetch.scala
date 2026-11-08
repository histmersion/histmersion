package com.histmersion
package domain.library.acquisition

import com.histmersion.config.LoadedConfig
import com.histmersion.pluginhost.FetchChain
import com.histmersion.util.plugins.Plugins
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.plugins.Plugin

/** The acquisition chain over the first-party fetchers (archive.org, Unpaywall, CORE, OpenAlex, PMC, arXiv, landing
  * pages, PMC's JATS text) — discovered and registered through the plugin host exactly as the server registers them,
  * with `loaded`'s settings, in `acquire.chain` order, over `http`. What a test or a one-off caller runs; the server
  * runs the chain of its own plugin generation, which may hold more fetchers or fewer.
  */
object OaFetch:
  type Bytes = Bytes.T
  object Bytes extends RefinedType[Array[Byte], Pure]
  type Md5 = Md5.T
  object Md5 extends RefinedType[String, Pure]

  def apply(loaded: LoadedConfig, http: Http, core: CoreBudget): FetchChain =
    val g = Plugins.fetchers(
      loaded,
      () => HttpPlugin(http),
      CoreBudget.quotas(core, CoreBudget.limits(loaded.config.acquire.core))
    )
    val cfg = loaded.config.acquire
    FetchChain.of(
      scala.collection.immutable.ListMap.from(
        g.registries.sourceFetchers.iterator.map((k, v) => Plugin.Id.assume(k.value) -> v)
      ),
      cfg.chainIds.flatMap(c => Plugin.Id.option(c.value)),
      FetchChain.AllowUnauthorised(cfg.allowUnauthorised.value),
      plugins = g.registered.map(p => Plugin.Id.assume(p.value))
    )._1

  /** Hex MD5 of a download, to compare with the one a PMC dataset record or a Zenodo record states; `None` when the
    * runtime offers no MD5 (the check is then skipped, never failed).
    */
  def md5(b: Bytes): Option[Md5] =
    scala.util.Try(java.security.MessageDigest.getInstance("MD5").digest(b.value).map("%02x".format(_)).mkString).toOption.map(Md5(_))
