package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class MediaCommonsConfig(apiUrl: MediaCommonsConfig.ApiUrl, batch: MediaCommonsConfig.Batch, maxlag: MediaCommonsConfig.Maxlag, licences: MediaCommonsConfig.Licences) derives ConfigReader

object MediaCommonsConfig:
  type ApiUrl = ApiUrl.T
  object ApiUrl extends RefinedType[String, Pure]
  type Batch = Batch.T
  object Batch extends RefinedType[Int, Pure]
  type Maxlag = Maxlag.T
  object Maxlag extends RefinedType[Int, Pure]
  type Licences = Licences.T
  object Licences extends RefinedType[String, Pure]
