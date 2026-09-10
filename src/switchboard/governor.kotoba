(ns switchboard.governor
  "TelephoneSwitchboardOperatorsGovernor — the independent safety/
  traceability layer for the ISCO-08 4223 community telephone
  switchboard operators actor (itonami actor pattern,
  ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. Switchboard twist: a
  destination extension is either a member of the registered
  extensions set or it is not (no misrouted call), and an outbound
  call's number is either a member of the registered do-not-call set
  or it is not — DNC exclusion is set membership, not operator
  discretion.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. line basis           — a call approval must cite a REGISTERED
                           line belonging to this client.
    4. extension membership — a proposed inbound routing's destination
                           extension must be a member of the line's
                           registered :extensions set.
    5. do-not-call exclusion — a proposed outbound call's number must
                           NOT be a member of the line's registered
                           :do-not-call set (exclusion is set
                           membership, not operator discretion).
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :approve-emergency-override (bypassing normal routing for
                           an emergency call).
    7. low confidence (< `confidence-floor`)."
  (:require [switchboard.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record l]
  (let [{:keys [op destination-extension outbound-number]} proposal
        route? (= :approve-inbound-route op)
        outbound? (= :approve-outbound-call op)
        line-op? (or route? outbound?)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and line-op? (nil? l))
      (conj {:rule :unknown-line :detail "未登録 line への承認は不可"})

      (and line-op? l (not= (:client-id l) (:client-id request)))
      (conj {:rule :line-wrong-client :detail "line が別 client のもの"})

      (and route? l destination-extension
           (not (contains? (:extensions l) destination-extension)))
      (conj {:rule :unknown-extension
             :detail (str "宛先内線 " destination-extension " は登録済み extensions 集合の外（誤配線禁止）")})

      (and outbound? l outbound-number
           (contains? (:do-not-call l) outbound-number))
      (conj {:rule :do-not-call-violation
             :detail (str "発信先 " outbound-number " は登録済み着信拒否リストの要素（DNC 除外はオペレータ裁量ではない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `switchboard.store/Store`. Pure — never
  mutates the store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        l (some->> (:line-id proposal) (store/line store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record l)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :approve-emergency-override (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
