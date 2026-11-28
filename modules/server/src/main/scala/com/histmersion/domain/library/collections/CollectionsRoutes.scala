package com.histmersion
package domain.library.collections

import com.histmersion.api.library.CollectionsApi
import com.histmersion.domain.Caller
import com.histmersion.domain.library.search.Search
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.api.Api
import com.histmersion.domain.Creator
import com.histmersion.domain.CreatorControllers
import com.histmersion.lint.{tolerated, LintRule}

/** The collections' endpoints bound to their controllers. Every request is the local person's until there are accounts.
  */
object CollectionsRoutes:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def apply(c: CollectionsControllers): List[ServerEndpoint[Any, Identity]] =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)
    val me = Caller.Local
    List(
      CollectionsApi.collections.serverLogic[Identity](_ => c.collections(me).left.map(Api.Error(_))),
      CollectionsApi.collection.serverLogic[Identity](name =>
        c.collection(me, CollectionsService.CollectionName(name.value))
      ),
      CollectionsApi.search.serverLogic[Identity]((name, q, limit, unlinked) =>
        c.search(
          me,
          CollectionsService.CollectionName(name.value),
          q.map(x => Search.Query(x.value)),
          limit.map(l => CollectionsService.Limit(l.value)),
          (if unlinked then CollectionsService.SearchUnlinked.Unlinked else CollectionsService.SearchUnlinked.All)
        )
      ),
      CollectionsApi.importItem.serverLogic[Identity]((name, req) =>
        CreatorControllers.rest(req)(c.importItem(me, CollectionsService.CollectionName(name.value), req))
      ),
      CollectionsApi.link.serverLogic[Identity]((name, req) =>
        CreatorControllers.rest(req)(c.link(me, CollectionsService.CollectionName(name.value), req))
      ),
      CollectionsApi.push.serverLogic[Identity]((name, req) =>
        c.push(me, CollectionsService.CollectionName(name.value), req)
      ),
      CollectionsApi.updateOne.serverLogic[Identity]((name, req) =>
        c.updateOne(me, CollectionsService.CollectionName(name.value), req)
      ),
      CollectionsApi.updateAll.serverLogic[Identity]((name, req) =>
        c.updateAll(me, CollectionsService.CollectionName(name.value), req)
      ),
      CollectionsApi.set.serverLogic[Identity]((name, req) =>
        c.set(me, CollectionsService.CollectionName(name.value), req)
      ),
      CollectionsApi.audit.serverLogic[Identity](name => c.audit(me, CollectionsService.CollectionName(name.value))),
      CollectionsApi.gaps.serverLogic[Identity]((name, limit) =>
        c.gaps(me, CollectionsService.CollectionName(name.value), limit.map(l => CollectionsService.Limit(l.value)))
      ),
      CollectionsApi.sync.serverLogic[Identity]((name, req) =>
        c.sync(me, CollectionsService.CollectionName(name.value), req)
      )
    )
