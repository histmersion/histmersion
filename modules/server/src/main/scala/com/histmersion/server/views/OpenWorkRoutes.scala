package com.histmersion
package server.views

import com.histmersion.api.research.ResearchApi
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

final class OpenWorkRoutes(view: OpenWorkView):
  def endpoints: List[ServerEndpoint[Any, Identity]] =
    List(
      ResearchApi.state.serverLogicSuccess[Identity](scope => view.state(scope.parsed)),
      ResearchApi.acquisition.serverLogic[Identity](view.acquisition)
    )
