package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class EventsLeadsConfig(
    km: EventsLeadsConfig.Km,
    years: EventsLeadsConfig.Years,
    articleKm: EventsLeadsConfig.ArticleKm,
    largeVei: EventsLeadsConfig.LargeVei,
    largeMagnitude: EventsLeadsConfig.LargeMagnitude,
    articleLimit: EventsLeadsConfig.ArticleLimit
) derives ConfigReader

object EventsLeadsConfig:
  type Km = Km.T
  object Km extends RefinedType[Double, Pure]
  type Years = Years.T
  object Years extends RefinedType[Long, Pure]
  type ArticleKm = ArticleKm.T
  object ArticleKm extends RefinedType[Double, Pure]
  type LargeVei = LargeVei.T
  object LargeVei extends RefinedType[Int, Pure]
  type LargeMagnitude = LargeMagnitude.T
  object LargeMagnitude extends RefinedType[Double, Pure]
  type ArticleLimit = ArticleLimit.T
  object ArticleLimit extends RefinedType[Int, Pure]
