package com.histmersion
package domain.automation.agents

import io.github.iltotore.iron.constraint.any.Pure

trait Stored[O]:
  def write(o: O): Stored.Json
  def read(json: Stored.Json): Either[Stored.Unread, O]

object Stored:
  type Json = Json.T
  object Json extends Newtype[String, Pure]
  type Unread = Unread.T
  object Unread extends Newtype[String, Pure]
