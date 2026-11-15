package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
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
  object Km extends Newtype[Double, Pure]
  type Years = Years.T
  object Years extends Newtype[Long, Pure]
  type ArticleKm = ArticleKm.T
  object ArticleKm extends Newtype[Double, Pure]
  type LargeVei = LargeVei.T
  object LargeVei extends Newtype[Int, Pure]
  type LargeMagnitude = LargeMagnitude.T
  object LargeMagnitude extends Newtype[Double, Pure]
  type ArticleLimit = ArticleLimit.T
  object ArticleLimit extends Newtype[Int, Pure]
