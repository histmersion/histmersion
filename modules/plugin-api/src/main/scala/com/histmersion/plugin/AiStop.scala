package com.histmersion
package plugin

/** Why the model stopped. */
enum AiStop derives CanEqual:
  case EndTurn, ToolUse, MaxTokens, Other
