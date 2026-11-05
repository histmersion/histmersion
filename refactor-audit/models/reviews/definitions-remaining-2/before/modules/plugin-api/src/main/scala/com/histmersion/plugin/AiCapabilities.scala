package com.histmersion.plugin

/** What a provider can do. A provider without `tools` is offered no tools (a step that needs them should not chain its
  * models); without `structuredOutput` the schema still travels in the request.
  *
  * @param iterationMarkers
  *   the conversation carries the notes the host's loop adds before each later turn (`[Iteration 2 of 6]`), as
  *   [[AiMessage.User]] messages where the loop puts them (the last message); off, they are left out
  * @param verbatimFailures
  *   the provider's refusals and failures are complete descriptions, shown as they are: the host adds neither the
  *   model's name to a [[AiProvider.check]] refusal nor the model and an exception's name to an [[AiFailure.Error]];
  *   off, it adds them
  */
final case class AiCapabilities(
    tools: Boolean = true,
    structuredOutput: Boolean = false,
    iterationMarkers: Boolean = false,
    verbatimFailures: Boolean = false
):
  /** The constructor of a provider compiled before the loop's notes and verbatim failures existed. */
  def this(tools: Boolean, structuredOutput: Boolean) = this(tools, structuredOutput, false, false)

object AiCapabilities:
  /** The capabilities as a provider compiled before the loop's notes and verbatim failures wrote them. */
  def apply(tools: Boolean, structuredOutput: Boolean): AiCapabilities =
    new AiCapabilities(tools, structuredOutput, false, false)
