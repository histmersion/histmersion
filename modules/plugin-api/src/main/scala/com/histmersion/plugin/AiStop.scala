package com.histmersion.plugin

/** Why the model stopped. */
enum AiStop:
  case EndTurn, ToolUse, MaxTokens, Other
