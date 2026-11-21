package com.histmersion
package fixtures.helloai

import com.histmersion.Newtype
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

final case class Reply(
    when: Reply.When,
    text: Reply.Text,
    tool: Option[Reply.Tool] = None,
    input: Option[Reply.Input] = None
) derives ConfigReader

object Reply:
  type When = When.T
  object When extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Tool = Tool.T
  object Tool extends Newtype[String, Pure]
  type Input = Input.T
  object Input extends Newtype[String, Pure]
