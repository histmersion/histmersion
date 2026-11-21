package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** A tool the step offers the model: its name, what it does, and its input as a JSON Schema (text). */
final case class AiTool(name: AiTool.Name, description: AiTool.Description, inputSchema: AiTool.InputSchema)

object AiTool:

  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type InputSchema = InputSchema.T
  object InputSchema extends Newtype[String, Pure]

  final case class Call(id: Call.Id, tool: Call.Tool, input: Call.Input)

  object Call:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Tool = Tool.T
    object Tool extends Newtype[String, Pure]
    type Input = Input.T
    object Input extends Newtype[String, Pure]
