package com.histmersion.plugin

/** A tool the step offers the model: its name, what it does, and its input as a JSON Schema (text). */
final case class AiTool(name: AiTool.Name, description: AiTool.Description, inputSchema: AiTool.InputSchema)

object AiTool:

  /** A tool call the model made: the call's id (echoed in its [[AiMessage.ToolResult]]), the tool, and its input as
    * JSON text.
    */
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InputSchema = InputSchema.T
  object InputSchema extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Call(id: Call.Id, tool: Call.Tool, input: Call.Input)

  object Call:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tool = Tool.T
    object Tool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Input = Input.T
    object Input extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
