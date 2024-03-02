(ns com.biffweb.example.postgres
  (:require [com.biffweb :as biff]
            [com.biffweb.example.postgres.email :as email]
            [com.biffweb.example.postgres.app :as app]
            [com.biffweb.example.postgres.home :as home]
            [com.biffweb.example.postgres.middleware :as mid]
            [com.biffweb.example.postgres.ui :as ui]
            [com.biffweb.example.postgres.worker :as worker]
            [com.biffweb.example.postgres.schema :as schema]
            [clojure.java.io :as io]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :as tn-repl]
            [malli.core :as malc]
            [malli.registry :as malr]
            [next.jdbc :as jdbc]
            [nrepl.cmdline :as nrepl-cmd])
  (:gen-class))

(def modules
  [app/module
   (biff/authentication-module {})
   home/module
   schema/module
   worker/module])

(def routes [["" {:middleware [mid/wrap-site-defaults]}
              (keep :routes modules)]
             ["" {:middleware [mid/wrap-api-defaults]}
              (keep :api-routes modules)]])

(def handler (-> (biff/reitit-handler {:routes routes})
                 mid/wrap-base-defaults))

(def static-pages (apply biff/safe-merge (map :static modules)))

(defn generate-assets! [ctx]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [ctx]
  (biff/add-libs)
  (biff/eval-files! ctx)
  (generate-assets! ctx)
  (biff/catchall (require 'com.biffweb.example.postgres-test))
  (test/run-all-tests #"com.biffweb.example.postgres.*-test"))

(def malli-opts
  {:registry (malr/composite-registry
              malc/default-registry
              (apply biff/safe-merge (keep :schema modules)))})

(def initial-system
  {:biff/modules #'modules
   :biff/send-email #'email/send-email
   :biff/handler #'handler
   :biff/malli-opts #'malli-opts
   :biff.beholder/on-save #'on-save
   :biff.middleware/on-error #'ui/on-error
   :biff.xtdb/tx-fns biff/tx-fns
   :com.biffweb.example.postgres/chat-clients (atom #{})})

(defonce system (atom {}))

(defn use-postgres [{:keys [biff/secret] :as ctx}]
  (let [ds (jdbc/get-datasource (secret :example/postgres-url))]
    (jdbc/execute! ds [(slurp (io/resource "migrations.sql"))])
    (assoc ctx :example/ds ds)))

(def components
  [biff/use-aero-config
   biff/use-xt
   use-postgres
   biff/use-queues
   biff/use-tx-listener
   biff/use-htmx-refresh
   biff/use-jetty
   biff/use-chime
   biff/use-beholder])

(defn start []
  (let [new-system (reduce (fn [system component]
                             (log/info "starting:" (str component))
                             (component system))
                           initial-system
                           components)]
    (reset! system new-system)
    (generate-assets! new-system)
    (log/info "System started.")
    (log/info "Go to" (:biff/base-url new-system))
    new-system))

(defn -main []
  (let [{:keys [biff.nrepl/args]} (start)]
    (apply nrepl-cmd/-main args)))

(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start)
  :done)
