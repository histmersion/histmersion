package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

final case class DbConfig(url: DbConfig.Url, user: DbConfig.User, password: DbConfig.Password, pool: DbPoolConfig) derives ConfigReader

object DbConfig:
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type User = User.T
  object User extends Newtype[String, Pure]
  type Password = Password.T
  object Password extends Newtype[String, Pure]
