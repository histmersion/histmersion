package com.histmersion.domain.library.acquisition
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.acquire.defaults`: the oldest publication year a discovery search considers. */
final case class SearchDefaults(minYear: SearchDefaults.MinYear) derives ConfigReader

object SearchDefaults:
  type MinYear = MinYear.T
  object MinYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
