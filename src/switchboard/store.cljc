(ns switchboard.store
  "SSoT for the ISCO-08 4223 community telephone switchboard operators
  actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors
  section). Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    line   — a registered switchboard line {:line-id :client-id :name
             :extensions #{ext-str} :do-not-call #{number-str}}.
             `:extensions` is the registered set a proposed routing's
             destination extension must be a member of (no misrouted
             call to an unregistered extension); `:do-not-call` is the
             registered exclusion set a proposed outbound call's
             number must NOT be a member of (DNC exclusion is set
             membership, not operator discretion).
    record — a committed operating record (approved routed call) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (line [s line-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-line! [s l])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (line [_ line-id] (get-in @a [:lines line-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-line! [s l]
    (swap! a assoc-in [:lines (:line-id l)] l) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :lines {} :records [] :ledger []}
                                   seed)))))
