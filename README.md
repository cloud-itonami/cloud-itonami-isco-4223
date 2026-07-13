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

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
