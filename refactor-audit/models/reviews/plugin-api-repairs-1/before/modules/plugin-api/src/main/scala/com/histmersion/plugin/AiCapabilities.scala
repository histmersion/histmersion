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
    tools: AiCapabilities.Tools = true,
    structuredOutput: AiCapabilities.StructuredOutput = false,
    iterationMarkers: AiCapabilities.IterationMarkers = false,
    verbatimFailures: AiCapabilities.VerbatimFailures = false
):
  /** The constructor of a provider compiled before the loop's notes and verbatim failures existed. */
  def this(tools: Boolean, structuredOutput: Boolean) = this(tools, structuredOutput, false, false)

object AiCapabilities:
  /** The capabilities as a provider compiled before the loop's notes and verbatim failures wrote them. */
  type ApplyTools = ApplyTools.T
  object ApplyTools extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyStructuredOutput = ApplyStructuredOutput.T
  object ApplyStructuredOutput extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Tools = Tools.T
  object Tools extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StructuredOutput = StructuredOutput.T
  object StructuredOutput extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IterationMarkers = IterationMarkers.T
  object IterationMarkers extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerbatimFailures = VerbatimFailures.T
  object VerbatimFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(tools: com.histmersion.plugin.AiCapabilities.ApplyTools, structuredOutput: com.histmersion.plugin.AiCapabilities.ApplyStructuredOutput): AiCapabilities =
    new AiCapabilities(tools, structuredOutput, false, false)
