(ns com.biffweb.examples.postgres.worker
  (:require [clojure.tools.logging :as log]
            [com.biffweb :as biff :refer [q]]
            [next.jdbc :as jdbc]
            [xtdb.api :as xt]))

(defn every-minute []
  (iterate #(biff/add-seconds % (* 5 60)) (java.util.Date.)))

(defn print-usage [{:keys [example/ds]}]
  ;; For a real app, you can have this run once per day and send you the output
  ;; in an email.
  (let [n-users (:count (jdbc/execute-one! ds ["SELECT count(*) FROM users"]))]
    (log/info "There are" n-users "users.")))

(defn echo-consumer [{:keys [biff/job] :as ctx}]
  (prn :echo job)
  (when-some [callback (:biff/callback job)]
    (callback job)))

(def plugin
  {:tasks [{:task #'print-usage
            :schedule every-minute}]
   :queues [{:id :echo
             :consumer #'echo-consumer}]})
