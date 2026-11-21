package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type UserId = UserId.T

object UserId extends Newtype[String, Pure]:
  val Local: UserId = UserId("local")
