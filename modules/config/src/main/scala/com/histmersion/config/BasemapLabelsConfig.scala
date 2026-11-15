package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
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
  object Url extends Newtype[String, Pure]
  type Files = Files.T
  object Files extends Newtype[String, Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends Newtype[Int, Pure]
  type Language = Language.T
  object Language extends Newtype[String, Pure]
  type Attribution = Attribution.T
  object Attribution extends Newtype[String, Pure]
