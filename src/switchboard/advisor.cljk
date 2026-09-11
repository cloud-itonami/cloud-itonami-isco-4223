(ns switchboard.advisor
  "TelephoneSwitchboardOperatorsAdvisor — proposes a call operation
  (approve an inbound route, approve an outbound call, approve an
  emergency override) for a registered organization. Swappable
  mock/llm; the advisor ONLY proposes — `switchboard.governor` checks
  extension membership and the do-not-call exclusion independently.
  Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-inbound-route|:approve-outbound-call|:approve-emergency-override
               :effect :propose :line-id str
               :destination-extension str :outbound-number str
               :stake kw :confidence n :rationale str}"
  ;; clojure.edn, not clojure.core/read-string: this parses untrusted
  ;; advisor output, and the core reader executes #=(...) at read time.
  (:require [clojure.edn :as edn]))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake line-id destination-extension outbound-number] :as request}]
  {:op op
   :effect :propose
   :line-id line-id
   :destination-extension destination-extension
   :outbound-number outbound-number
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a telephone switchboard operator advisor. Given a request,
   propose an :op, the :line-id, :destination-extension and/or
   :outbound-number, an honest :confidence and a :stake. Never call an
   unregistered-extension route or a do-not-call outbound call
   conforming — the governor checks both against the registered line
   record.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
