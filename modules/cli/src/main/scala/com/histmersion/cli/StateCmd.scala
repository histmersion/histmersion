package com.histmersion
package cli

import com.histmersion.api.automation.Queue
import com.histmersion.api.research.{OpenWork, ResearchApi}
import com.histmersion.util.console.Console

object StateCmd:

  enum Command extends Cli.Command:
    case State(scope: OpenWork.Scope, json: Cli.JsonOutput)
    case Why(item: Queue.QueueItem.Id, json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.State(scope, json) =>
          c.call(ResearchApi.state)(scope).map(s => Output.emit(json, s)(_.markdown))
        case Command.Why(item, json) =>
          c.call(ResearchApi.acquisition)(item).map(h => Output.emit(json, h)(_.markdown))
