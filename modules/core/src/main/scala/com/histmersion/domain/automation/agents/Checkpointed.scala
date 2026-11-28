package com.histmersion
package domain.automation.agents

import io.github.iltotore.iron.constraint.any.Pure

trait Checkpointed[O]:
  def write(o: O): Checkpointed.Json
  def read(json: Checkpointed.Json): Either[Checkpointed.Unread, O]

object Checkpointed:
  type Json = Json.T
  object Json extends Newtype[String, Pure]:
    def of(text: String): Json = Json(text)
    extension (json: Json)
      def text: String = json.value
      def parsed[A](parse: String => Either[String, A]): Either[Unread, A] = parse(json.value).left.map(Unread.of)
  type Unread = Unread.T
  object Unread extends Newtype[String, Pure]:
    def of(reason: String): Unread = Unread(reason)
    extension (unread: Unread) def reason: String = unread.value
