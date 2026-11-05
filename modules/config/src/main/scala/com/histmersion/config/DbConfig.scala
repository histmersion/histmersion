package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class DbConfig(url: DbConfig.Url, user: DbConfig.User, password: DbConfig.Password, pool: DbPoolConfig) derives ConfigReader

object DbConfig:
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type User = User.T
  object User extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Password = Password.T
  object Password extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
