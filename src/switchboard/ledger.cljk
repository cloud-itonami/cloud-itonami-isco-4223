(ns switchboard.ledger
  "Append-only audit ledger for the ISCO-08 4223 community telephone
  switchboard operators actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section). The third governance component alongside
  `switchboard.governor` (which decides) and `switchboard.store`
  (which persists): this namespace owns **what a ledger entry means**
  and refuses to write one that misreports the governance decision.

  Why it exists. Until this namespace, both terminal nodes of the
  actor graph wrote their own ad-hoc map and the commit path dropped
  the verdict entirely:

      :commit -> {:disposition :commit :record record}
      :hold   -> {:disposition :hold   :verdict verdict}

  So a proposal the governor passed cleanly and a proposal the
  governor ESCALATED — interrupting the graph until a human signed
  off — landed in the ledger as the same entry. Measured 2026-09-06 on
  two runs of the same `:approve-inbound-route` op against the same
  registered line, differing only in advisor confidence (0.95 vs 0.10,
  i.e. below `governor/confidence-floor`): the second run reported
  `:status :interrupted` and required `actor/approve!`, and both
  ledger entries read `:disposition :commit` with no verdict at all.
  Reconstructing 'did a human have to sign this off?' from the ledger
  meant re-deriving the governor's own rules from the advisor's
  self-reported confidence. An audit ledger that cannot answer that
  question is not an audit ledger.

  The entry vocabulary, one disposition per governance outcome:

    :commit                 the governor passed it; nobody was asked.
    :commit-after-approval  the governor ESCALATED it and a human
                            resumed the interrupted thread.
    :hold                   the governor refused it (HARD invariant).

  `append!` is the only supported write path and is fail-closed: it
  throws rather than record an entry that misreports the decision, so
  a mis-wired caller cannot quietly leave a true-looking trail. The
  refusals are the point of the namespace, not shape-checking."
  (:require [switchboard.store :as store]))

(def dispositions
  "Every disposition the ledger will record."
  #{:commit :commit-after-approval :hold})

(def commit-dispositions
  "The dispositions under which an operating record was actually written."
  #{:commit :commit-after-approval})

(defn commit-disposition
  "The commit disposition the ledger REQUIRES for `verdict`. Callers
  derive the disposition here rather than hard-coding `:commit`, so
  the escalated path cannot be recorded as a clean pass. `violations`
  checks the same fact independently, on every write."
  [verdict]
  (if (:escalate? verdict) :commit-after-approval :commit))

(defn entry
  "Build a ledger entry carrying the governor's `verdict` as evidence.
  `record` is the committed operating record for a commit disposition
  and nil for `:hold`."
  ([disposition verdict] (entry disposition verdict nil))
  ([disposition verdict record]
   (cond-> {:disposition disposition :verdict verdict}
     (some? record) (assoc :record record))))

(defn violations
  "Rules an entry must satisfy to be admissible, as a vector of
  {:rule :detail}. Empty means admissible. Pure."
  [{:keys [disposition verdict record]}]
  (let [known?     (contains? dispositions disposition)
        committed? (contains? commit-dispositions disposition)
        v?         (map? verdict)]
    (cond-> []
      (not known?)
      (conj {:rule :unknown-disposition
             :detail (str "disposition " (pr-str disposition) " は "
                          (pr-str (sort dispositions)) " のいずれでもない")})

      (not v?)
      (conj {:rule :no-verdict
             :detail "governor の verdict を伴わない entry は監査証跡にならない（証拠の無い記帳）"})

      (and committed? v? (:hard? verdict))
      (conj {:rule :committed-over-hard-refusal
             :detail "governor が HARD 違反として拒否した提案を commit として記帳することはできない"})

      (and (= :commit disposition) v? (:escalate? verdict))
      (conj {:rule :escalation-not-recorded
             :detail "escalate された提案は :commit ではなく :commit-after-approval（人の署名を落とさない）"})

      (and (= :commit-after-approval disposition) v? (not (:escalate? verdict)))
      (conj {:rule :approval-not-required
             :detail "escalate されていない提案に人の承認を騙ることはできない"})

      (and committed? (nil? record))
      (conj {:rule :commit-without-record
             :detail "commit の記帳には、実際に書かれた operating record が要る"})

      (and (= :hold disposition) (some? record))
      (conj {:rule :hold-with-record
             :detail ":hold は書込を伴わない —— record を持つ hold は矛盾"}))))

(defn admissible?
  "True when `e` may be appended."
  [e]
  (empty? (violations e)))

(defn append!
  "The only supported ledger write path. Fail-closed: throws
  `ex-info` (data `{:violations :entry}`) rather than record an entry
  that misreports the governance decision. Returns `store`."
  [store e]
  (let [vs (violations e)]
    (when (seq vs)
      (throw (ex-info (str "ledger は記帳を拒否した: "
                           (pr-str (mapv :rule vs)))
                      {:violations vs :entry e})))
    (store/append-ledger! store e)))

(defn human-approved?
  "Did a human have to sign this entry off? The question the ledger
  could not answer before this namespace existed."
  [e]
  (= :commit-after-approval (:disposition e)))

(defn escalated-commits
  "Every committed operation that reached the record only because a
  human resumed the interrupted thread."
  [entries]
  (filterv human-approved? entries))
