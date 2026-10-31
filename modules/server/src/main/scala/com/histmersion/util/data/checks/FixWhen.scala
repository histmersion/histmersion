package com.histmersion.util.data.checks

/** When `lint --fix` applies a rule's safe fix: on every call, or only when the call names the rule. */
enum FixWhen:
  case EveryFix, WhenNamed
