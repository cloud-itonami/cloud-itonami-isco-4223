(ns switchboard.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [switchboard.store :as store]
            [switchboard.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-line! st {:line-id "L-1" :client-id "client-1"
                              :name "main-trunk"
                              :extensions #{"101" "102" "103"}
                              :do-not-call #{"+15550001111"}})
    st))

(defn- route [ext]
  {:op :approve-inbound-route :effect :propose :line-id "L-1"
   :destination-extension ext :confidence 0.9 :stake :low})

(defn- outbound [number]
  {:op :approve-outbound-call :effect :propose :line-id "L-1"
   :outbound-number number :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-route-to-registered-extension
  (let [st (fresh-store)
        v (governor/check req {} (route "101") st)]
    (is (:ok? v))))

(deftest hard-on-unregistered-extension
  (testing "misrouting to an unregistered extension is not permitted"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (route "999") :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :unknown-extension (:rule %)) (:violations v))))))

(deftest ok-outbound-to-non-dnc-number
  (let [st (fresh-store)
        v (governor/check req {} (outbound "+15559998888") st)]
    (is (:ok? v))))

(deftest hard-on-do-not-call-violation
  (testing "DNC exclusion is set membership, not operator discretion"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (outbound "+15550001111") :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :do-not-call-violation (:rule %)) (:violations v))))))

(deftest hard-on-unknown-line
  (let [st (fresh-store)
        v (governor/check req {} (assoc (route "101") :line-id "L-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-line (:rule %)) (:violations v)))))

(deftest hard-on-foreign-line
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (route "101") st)]
      (is (:hard? v))
      (is (some #(= :line-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (route "101") st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (route "101") :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-emergency-override
  (let [st (fresh-store)
        v (governor/check req {} {:op :approve-emergency-override :effect :propose
                                  :line-id "L-1" :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (route "101") :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
