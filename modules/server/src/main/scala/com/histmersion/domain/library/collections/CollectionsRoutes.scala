package com.histmersion.domain.library.collections

import com.histmersion.api.library.CollectionsApi
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The collections' endpoints bound to their controllers. Every request is the local person's until there are accounts.
  */
object CollectionsRoutes:
  def apply(c: CollectionsControllers): List[ServerEndpoint[Any, Identity]] =
    val me = Caller.Local
    List(
      CollectionsApi.collections.serverLogic[Identity](_ => c.collections(me)),
      CollectionsApi.collection.serverLogic[Identity](name => c.collection(me, name)),
      CollectionsApi.search.serverLogic[Identity]((name, q, limit, unlinked) => c.search(me, name, q, limit, unlinked)),
      CollectionsApi.importItem.serverLogic[Identity]((name, req) => c.importItem(me, name, req)),
      CollectionsApi.link.serverLogic[Identity]((name, req) => c.link(me, name, req)),
      CollectionsApi.push.serverLogic[Identity]((name, req) => c.push(me, name, req)),
      CollectionsApi.updateOne.serverLogic[Identity]((name, req) => c.updateOne(me, name, req)),
      CollectionsApi.updateAll.serverLogic[Identity]((name, req) => c.updateAll(me, name, req)),
      CollectionsApi.set.serverLogic[Identity]((name, req) => c.set(me, name, req)),
      CollectionsApi.audit.serverLogic[Identity](name => c.audit(me, name)),
      CollectionsApi.gaps.serverLogic[Identity]((name, limit) => c.gaps(me, name, limit)),
      CollectionsApi.sync.serverLogic[Identity]((name, req) => c.sync(me, name, req))
    )
