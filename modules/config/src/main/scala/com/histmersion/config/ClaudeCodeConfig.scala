package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** The `claude-code` provider: the person's own `claude` CLI, run headlessly (`claude -p`) on their own Claude login
  * (docs/research-model-providers.md §1 — their own machine, their own use).
  *
  *   - `command`: the executable (an absolute path when the server's PATH lacks it — a LaunchAgent's usually does).
  *   - `configDir`: a `CLAUDE_CONFIG_DIR` of its own ("" = the user's `~/.claude`); a separate one keeps the user's
  *     hooks, memory and MCP servers out of the runs, but needs its own `/login` once.
  *   - `extraArgs`: appended to every call, whitespace-separated (`--restricted` by default: the user's settings files
  *     and code-running tools stay out).
  *   - `useApiKey`: false (the default) strips `ANTHROPIC_API_KEY`/`ANTHROPIC_AUTH_TOKEN` from the CLI's environment —
  *     `claude -p` always prefers a key that is present, which would silently bill the API.
  *   - `allowNonLoopback`: the provider is refused while the server listens beyond loopback, so nobody else's request
  *     can spend the owner's plan; true = every client of this server is its owner.
  *   - `mcpUrl`: the server's read-only agent MCP endpoint as the CLI reaches it ("" = `public-url` + `/mcp/agent`).
  *   - `limitCooldownMinutes`: after the CLI reports the plan's usage limit without a reset time, the model is not used
  *     for this long.
  *   - `disableSlashCommands`: `--disable-slash-commands` on every run — no skill or slash command is loaded (a role's
  *     skills are inlined into its generated prompt; a main-thread `--agent` gets no `skills:` preload either way).
  *   - `env`: variables set in the CLI's environment on every call; a variable whose value is empty (`""` or `null`) is
  *     removed, also one the server inherited ([[ClaudeCodeConfig.envChanges]]).
  */
final case class ClaudeCodeConfig(
    command: ClaudeCodeConfig.Command,
    configDir: ClaudeCodeConfig.ConfigDir,
    extraArgs: ClaudeCodeConfig.ExtraArgs,
    useApiKey: ClaudeCodeConfig.UseApiKey,
    allowNonLoopback: ClaudeCodeConfig.AllowNonLoopback,
    mcpUrl: ClaudeCodeConfig.McpUrl,
    timeoutSeconds: ClaudeCodeConfig.TimeoutSeconds,
    limitCooldownMinutes: ClaudeCodeConfig.LimitCooldownMinutes,
    disableSlashCommands: ClaudeCodeConfig.DisableSlashCommands,
    env: Map[ClaudeCodeConfig.EnvKey, Option[ClaudeCodeConfig.EnvValue]]
) derives ConfigReader:
  /** [[env]] as changes to an environment, sorted by name: `Some(value)` sets, `None` removes. */
  def envChanges: List[(ClaudeCodeConfig.EnvKey, Option[ClaudeCodeConfig.EnvValue])] =
    env.toList.map((k, v) => ClaudeCodeConfig.EnvKey(k.value.trim) -> v.filter(_.value.trim.nonEmpty)).filter(_._1.value.nonEmpty).sortBy(_._1.value)

object ClaudeCodeConfig:
  type Command = Command.T
  object Command extends Newtype[String, Pure]
  type ConfigDir = ConfigDir.T
  object ConfigDir extends Newtype[String, Pure]
  type ExtraArgs = ExtraArgs.T
  object ExtraArgs extends Newtype[String, Pure]
  type UseApiKey = UseApiKey.T
  object UseApiKey extends Newtype[Boolean, Pure]
  type AllowNonLoopback = AllowNonLoopback.T
  object AllowNonLoopback extends Newtype[Boolean, Pure]
  type McpUrl = McpUrl.T
  object McpUrl extends Newtype[String, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]
  type LimitCooldownMinutes = LimitCooldownMinutes.T
  object LimitCooldownMinutes extends Newtype[Int, Pure]
  type DisableSlashCommands = DisableSlashCommands.T
  object DisableSlashCommands extends Newtype[Boolean, Pure]
  type EnvKey = EnvKey.T
  object EnvKey extends Newtype[String, Pure]:
    given ConfigReader[Map[EnvKey, Option[EnvValue]]] =
      ConfigReader.derived[Map[String, Option[EnvValue]]].emap { byName =>
        byName.foldLeft[Either[String, Map[EnvKey, Option[EnvValue]]]](Right(Map.empty)) { case (acc, (name, v)) =>
          for
            done <- acc
            k <- EnvKey.either(name)
          yield done.updated(k, v)
        }
      }
  type EnvValue = EnvValue.T
  object EnvValue extends Newtype[String, Pure]
