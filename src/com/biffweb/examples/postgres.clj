(ns com.biffweb.examples.postgres
  (:require [com.biffweb :as biff]
            [com.biffweb.examples.postgres.auth-plugin :as auth-plugin]
            [com.biffweb.examples.postgres.email :as email]
            [com.biffweb.examples.postgres.app :as app]
            [com.biffweb.examples.postgres.home :as home]
            [com.biffweb.examples.postgres.middleware :as mid]
            [com.biffweb.examples.postgres.ui :as ui]
            [com.biffweb.examples.postgres.worker :as worker]
            [clojure.java.io :as io]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :as tn-repl]
            [next.jdbc :as jdbc]
            [nrepl.cmdline :as nrepl-cmd]))

(def plugins
  [app/plugin
   (auth-plugin/plugin {})
   home/plugin
   worker/plugin])

(def routes [["" {:middleware [mid/wrap-site-defaults]}
              (keep :routes plugins)]
             ["" {:middleware [mid/wrap-api-defaults]}
              (keep :api-routes plugins)]])

(def handler (-> (biff/reitit-handler {:routes routes})
                 mid/wrap-base-defaults))

(def static-pages (apply biff/safe-merge (map :static plugins)))

(defn generate-assets! [ctx]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [ctx]
  (biff/add-libs)
  (biff/eval-files! ctx)
  (generate-assets! ctx)
  (test/run-all-tests #"com.biffweb.examples.postgres.test.*"))

(def initial-system
  {:biff/plugins #'plugins
   :biff/merge-context-fn identity
   :biff/send-email #'email/send-email
   :biff/handler #'handler
   :biff.beholder/on-save #'on-save
   :biff.middleware/on-error #'ui/on-error
   :example/chat-clients (atom #{})})

(defonce system (atom {}))

(defn use-postgres [{:keys [biff/secret] :as ctx}]
  (let [ds (jdbc/get-datasource (secret :example/postgres-url))]
    (jdbc/execute! ds [(slurp (io/resource "migrations.sql"))])
    (assoc ctx :example/ds ds)))

(def components
  [biff/use-config
   biff/use-secrets
   use-postgres
   biff/use-queues
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
    (log/info "Go to" (:biff/base-url new-system))))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))

(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start))
