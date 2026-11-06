package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object Km extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Years = Years.T
  object Years extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleKm = ArticleKm.T
  object ArticleKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LargeVei = LargeVei.T
  object LargeVei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LargeMagnitude = LargeMagnitude.T
  object LargeMagnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleLimit = ArticleLimit.T
  object ArticleLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
