package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.Api
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The attestations' endpoints bound to their controllers, in their old order. */
object AttestationsRoutes:
  def apply(attestations: AttestationsControllers): List[ServerEndpoint[Any, Identity]] =
    val me = Caller.Local
    List(
      Api.attestations.serverLogicSuccess[Identity](q =>
        attestations.list(q.citekey, q.targetType, q.targetId, q.limit, q.offset)
      ),
      Api.getAttestation.serverLogic[Identity](id => attestations.get(id)),
      Api.createAttestation.serverLogic[Identity](in => attestations.create(me, in)),
      Api.patchAttestation.serverLogic[Identity]((id, p) => attestations.patch(me, id, p)),
      Api.deleteAttestation.serverLogic[Identity](id => attestations.delete(me, id))
    )
