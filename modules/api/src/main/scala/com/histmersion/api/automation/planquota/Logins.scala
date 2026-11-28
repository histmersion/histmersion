package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.TwoStates

final case class Logins(
    claudeCodeApiKey: Logins.ClaudeCodeApiKey = Logins.ClaudeCodeApiKey.Login,
    codexApiKey: Logins.CodexApiKey = Logins.CodexApiKey.Login,
    opencodeChatgpt: Logins.OpencodeChatgpt = Logins.OpencodeChatgpt.Other
)

object Logins:
  enum ClaudeCodeApiKey derives CanEqual:
    case ApiKey, Login
  object ClaudeCodeApiKey extends TwoStates[ClaudeCodeApiKey]:
    extension (claudeCodeApiKey: ClaudeCodeApiKey) def claudeCodeApiKey: Boolean = claudeCodeApiKey == ApiKey
  enum CodexApiKey derives CanEqual:
    case ApiKey, Login
  object CodexApiKey extends TwoStates[CodexApiKey]:
    extension (codexApiKey: CodexApiKey) def codexApiKey: Boolean = codexApiKey == ApiKey
  enum OpencodeChatgpt derives CanEqual:
    case Chatgpt, Other
  object OpencodeChatgpt extends TwoStates[OpencodeChatgpt]:
    extension (opencodeChatgpt: OpencodeChatgpt) def opencodeChatgpt: Boolean = opencodeChatgpt == Chatgpt
