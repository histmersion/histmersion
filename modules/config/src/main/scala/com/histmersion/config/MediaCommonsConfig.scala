package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

final case class MediaCommonsConfig(
    apiUrl: MediaCommonsConfig.ApiUrl,
    batch: MediaCommonsConfig.Batch,
    maxlag: MediaCommonsConfig.Maxlag,
    licences: MediaCommonsConfig.Licences
) derives ConfigReader

object MediaCommonsConfig:
  type ApiUrl = ApiUrl.T
  object ApiUrl extends Newtype[String, Pure]
  type Batch = Batch.T
  object Batch extends Newtype[Int, Pure]
  type Maxlag = Maxlag.T
  object Maxlag extends Newtype[Int, Pure]
  type Licences = Licences.T
  object Licences extends Newtype[String, Pure]
