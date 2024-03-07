(ns com.biffweb.example.postgres.worker
  (:require [clojure.tools.logging :as log]
            [com.biffweb :as biff :refer [q]]
            [next.jdbc :as jdbc]))

(defn every-n-minutes [n]
  (iterate #(biff/add-seconds % (* 60 n)) (java.util.Date.)))

(defn print-usage [{:keys [example/ds]}]
  ;; For a real app, you can have this run once per day and send you the output
  ;; in an email.
  (let [n-users (:count (jdbc/execute-one! ds ["SELECT count(*) FROM users"]))]
    (log/info "There are" n-users "users.")))

(defn echo-consumer [{:keys [biff/job] :as ctx}]
  (prn :echo job)
  (when-some [callback (:biff/callback job)]
    (callback job)))

(def module
  {:tasks [{:task #'print-usage
            :schedule #(every-n-minutes 5)}]
   :queues [{:id :echo
             :consumer #'echo-consumer}]})
