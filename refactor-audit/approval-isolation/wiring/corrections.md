# Wiring preview correction

Compiling the complete preview caught a fourth TestRuns.proposals caller, formatted
as a multiline selection. The original `.syntax` equality matched only three calls.
The corrected matcher uses the Term.Select/Term.Name structure and requires all four.
The first plan and compiler log at target/approval-wiring-verification/preview.log
remain preserved. It was not applied. V2 is the corrected preview and plan.
