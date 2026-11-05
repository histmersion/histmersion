# Preview correction

The first generated preview failed compilation because the moved insertApproval
method was emitted at top-level indentation rather than inside AutomationState.
Scalameta parsing accepted that as a top-level method; compilation caught the lost
companion ownership. The v2 generator retains the member indentation. The original
preview and plan are preserved, and no application files were modified by either run.

Original compiler evidence: target/approval-extraction-verification/preview.log.
The v2 directory retains the next plan. Compilation then caught a shadowed name:
the methods' ApprovalState parameter hid the class's AtomicReference state after
the insertion helper was moved. V3 explicitly selects `this.state` at the rewritten
call sites, preserving which state the original private helper captured. The V2
compiler evidence remains in target/approval-extraction-verification-v2/preview.log.
Neither failed preview was applied. V3 contains the current plan and verification.
