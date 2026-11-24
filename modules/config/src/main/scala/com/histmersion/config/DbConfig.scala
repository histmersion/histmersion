package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import com.histmersion.lint.{FeaturePlace, movesTo}

/** @param instanceLockCheckSeconds
  *   how often the server looks whether the session holding its database's instance lock (one server per database) is
  *   still there, and takes the lock again when the database dropped it.
  */
@movesTo(FeaturePlace.UtilDatabase)
final case class DbConfig(
    url: DbConfig.Url,
    user: DbConfig.User,
    password: DbConfig.Password,
    pool: DbPoolConfig,
    instanceLockCheckSeconds: DbConfig.InstanceLockCheckSeconds
) derives ConfigReader

object DbConfig:
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type User = User.T
  object User extends Newtype[String, Pure]
  type Password = Password.T
  object Password extends Newtype[String, Pure]
  type InstanceLockCheckSeconds = InstanceLockCheckSeconds.T
  object InstanceLockCheckSeconds extends Newtype[Int, Pure]:
    extension (s: InstanceLockCheckSeconds)
      /** The interval as a duration: at least a second. */
      def duration: FiniteDuration = FiniteDuration(math.max(1, s.value).toLong, TimeUnit.SECONDS)
