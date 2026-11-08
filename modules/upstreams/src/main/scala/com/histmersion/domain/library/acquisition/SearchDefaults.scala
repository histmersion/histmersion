package com.histmersion
package domain.library.acquisition
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `histmersion.acquire.defaults`: the oldest publication year a discovery search considers. */
final case class SearchDefaults(minYear: SearchDefaults.MinYear) derives ConfigReader

object SearchDefaults:
  type MinYear = MinYear.T
  object MinYear extends Newtype[Int, Pure]
