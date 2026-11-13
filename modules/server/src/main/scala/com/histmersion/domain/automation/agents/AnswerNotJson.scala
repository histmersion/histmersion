package com.histmersion
package domain.automation.agents

/** Why a model's text answer is not the JSON object its step asks for: where the parser stopped, or that the text holds
  * no object at all.
  */
final case class AnswerNotJson(message: String) extends Exception(message)
