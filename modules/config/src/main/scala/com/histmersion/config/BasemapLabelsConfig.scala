package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The physical names (seas, ranges, deserts, lakes, rivers, peaks …): Natural Earth's label layers, `url` with
  * `{file}` for each of `files`, cut to the region and written as a small vector archive of zooms 0…`maxZoom`;
  * `language` is the names a style shows unless it says otherwise.
  */
final case class BasemapLabelsConfig(
    url: BasemapLabelsConfig.Url,
    files: List[BasemapLabelsConfig.Files],
    maxZoom: BasemapLabelsConfig.MaxZoom,
    language: BasemapLabelsConfig.Language,
    attribution: BasemapLabelsConfig.Attribution
) derives ConfigReader

object BasemapLabelsConfig:
  type Url = Url.T
  object Url extends RefinedType[String, Pure]
  type Files = Files.T
  object Files extends RefinedType[String, Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends RefinedType[Int, Pure]
  type Language = Language.T
  object Language extends RefinedType[String, Pure]
  type Attribution = Attribution.T
  object Attribution extends RefinedType[String, Pure]
