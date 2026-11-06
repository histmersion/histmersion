package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Files = Files.T
  object Files extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Language = Language.T
  object Language extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Attribution = Attribution.T
  object Attribution extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
