package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The `codex` provider: the person's own `codex` CLI (`codex exec --json`), on the ChatGPT plan login of a
  * `CODEX_HOME` of its own (the generated `<runtimes>/codex/home`, or `home`), never a copied `auth.json`
  * (docs/research-agent-runtimes.md §1.2).
  *
  *   - `useApiKey`: false strips `OPENAI_API_KEY`/`CODEX_API_KEY` from its environment (a key bills the API).
  *   - `appServer`: poll the plan's windows with `codex app-server` → `account/rateLimits/read` (it generates nothing;
  *     reset credits are never touched).
  *   - `toolOutputTokens`: the generated home's `tool_output_token_limit` — the most one tool output (a code-mode
  *     `exec` batch of calls) puts in the context; 0 leaves the model's own (10,000 tokens).
  *   - `baseInstructions`: `lean` writes Histmersion's own base prompt into the home (`model_instructions_file`, from
  *     `agents/_shared/codex-base-instructions.md`) in place of Codex's built-in one (~21k characters of collaboration,
  *     PR, skills and plugin guidance, re-sent on every request); `builtin` keeps Codex's.
  */
final case class CodexConfig(
    command: String,
    home: String,
    useApiKey: Boolean,
    timeoutSeconds: Int,
    appServer: Boolean,
    toolOutputTokens: Int = 0,
    baseInstructions: String = "lean",
    /** Run transcripts older than this many days are deleted from the home (#2582); 0 keeps all. */
    keepSessionsDays: Int = 14,
    /** Vacuum the home's SQLite files (with [[sqlite]]) in the same pass. */
    vacuum: Boolean = true,
    /** At most one pass this often, after a step, while no run uses the home; 0 = never by itself. */
    retentionEveryHours: Int = 24,
    /** The `sqlite3` executable the vacuum runs. */
    sqlite: String = "sqlite3"
) derives ConfigReader:
  /** `builtin` (any case) keeps Codex's own base prompt; anything else is `lean`. */
  def leanBase: Boolean = !baseInstructions.trim.equalsIgnoreCase("builtin")
