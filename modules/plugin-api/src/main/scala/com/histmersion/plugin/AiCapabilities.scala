package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** What a provider can do. A provider without `tools` is offered no tools (a step that needs them should not chain its
  * models); without `structuredOutput` the schema still travels in the request.
  *
  * @param iterationMarkers
  *   the conversation carries the notes the host's loop adds before each later turn (`[Iteration 2 of 6]`), as
  *   [[AiMessage.User]] messages where the loop puts them (the last message); off, they are left out
  * @param verbatimFailures
  *   the provider's refusals and failures are complete descriptions, shown as they are: the host adds neither the
  *   model's name to a [[AiProvider.check]] refusal nor the model and an exception's name to an error; off, it adds
  *   them
  */
final case class AiCapabilities(
    tools: AiCapabilities.Tools = AiCapabilities.Tools(true),
    structuredOutput: AiCapabilities.StructuredOutput = AiCapabilities.StructuredOutput(false),
    iterationMarkers: AiCapabilities.IterationMarkers = AiCapabilities.IterationMarkers(false),
    verbatimFailures: AiCapabilities.VerbatimFailures = AiCapabilities.VerbatimFailures(false)
):
  /** The constructor of a provider compiled before the loop's notes and verbatim failures existed. */
  def this(tools: Boolean, structuredOutput: Boolean) = this(
    AiCapabilities.Tools(tools),
    AiCapabilities.StructuredOutput(structuredOutput),
    AiCapabilities.IterationMarkers(false),
    AiCapabilities.VerbatimFailures(false)
  )

object AiCapabilities:
  /** The capabilities as a provider compiled before the loop's notes and verbatim failures wrote them. */
  type ApplyTools = Tools
  val ApplyTools: Tools.type = Tools
  type ApplyStructuredOutput = StructuredOutput
  val ApplyStructuredOutput: StructuredOutput.type = StructuredOutput

  type Tools = Tools.T
  object Tools extends Newtype[Boolean, Pure]
  type StructuredOutput = StructuredOutput.T
  object StructuredOutput extends Newtype[Boolean, Pure]
  type IterationMarkers = IterationMarkers.T
  object IterationMarkers extends Newtype[Boolean, Pure]
  type VerbatimFailures = VerbatimFailures.T
  object VerbatimFailures extends Newtype[Boolean, Pure]

  def apply(tools: ApplyTools, structuredOutput: ApplyStructuredOutput): AiCapabilities =
    new AiCapabilities(tools, structuredOutput, IterationMarkers(false), VerbatimFailures(false))
