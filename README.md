# cloud-itonami-isco-4223

Open Business Blueprint for **ISCO-08 4223**: Telephone Switchboard Operators — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — TelephoneSwitchboardOperatorsAdvisor ⊣
TelephoneSwitchboardOperatorsGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
13 tests / 27 assertions green.

The call-routing HARD invariants — set membership, not operator
discretion:

1. **Extension membership** — an inbound routing's destination
   extension must be a member of the registered extensions set (no
   misrouted call).
2. **Do-not-call exclusion** — an outbound call's number must NOT be a
   member of the registered do-not-call set.

Also HARD: unregistered/foreign line, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:approve-emergency-override` (bypassing normal routing for an
emergency call), low confidence (< 0.6).

## The audit ledger

`switchboard.ledger` owns what a ledger entry means and is the only
supported write path. One disposition per governance outcome:

| disposition | meaning |
| --- | --- |
| `:commit` | the governor passed it; nobody was asked |
| `:commit-after-approval` | the governor ESCALATED it and a human resumed the interrupted thread |
| `:hold` | the governor refused it (HARD invariant) |

Every entry carries the governor's verdict as evidence, so
`ledger/human-approved?` and `ledger/escalated-commits` answer *which
committed operations needed a human* directly from the trail, rather
than by re-deriving the governor's rules from the advisor's
self-reported confidence.

`ledger/append!` is fail-closed — it throws rather than record an entry
that misreports the decision, and refuses each of these by name:
`:no-verdict`, `:escalation-not-recorded`, `:approval-not-required`,
`:committed-over-hard-refusal`, `:commit-without-record`,
`:hold-with-record`, `:unknown-disposition`.

This closes a defect measured 2026-09-06: two runs of the same
`:approve-inbound-route` op against the same registered line, differing
only in advisor confidence (0.95 vs 0.10, i.e. below
`governor/confidence-floor`), produced the same ledger entry
`{:disposition :commit :record ...}` with no verdict — even though the
second run reported `:status :interrupted` and only committed because
`actor/approve!` was called. Escalation was invisible in the audit
trail. Each refusal above was watched failing before it was landed.

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
