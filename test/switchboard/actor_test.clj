(ns switchboard.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [switchboard.actor :as actor]
            [switchboard.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-line! st {:line-id "L-1" :client-id "client-1"
                              :name "main-trunk"
                              :extensions #{"101"}
                              :do-not-call #{"+15550001111"}})
    st))

(deftest commits-a-registered-extension-route
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-inbound-route :stake :low
                 :line-id "L-1" :destination-extension "101"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-a-dnc-outbound-call
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-outbound-call :stake :low
                 :line-id "L-1" :outbound-number "+15550001111"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-overrides-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-emergency-override :stake :high
                 :line-id "L-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
