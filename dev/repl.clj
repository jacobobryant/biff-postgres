(ns repl
  (:require [com.biffweb.example.postgres :as main]
            [com.biffweb.example.postgres.util.postgres :as util-pg]
            [com.biffweb :as biff :refer [q]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [next.jdbc :as jdbc]))

;; REPL-driven development
;; ----------------------------------------------------------------------------------------
;; If you're new to REPL-driven development, Biff makes it easy to get started: whenever
;; you save a file, your changes will be evaluated. Biff is structured so that in most
;; cases, that's all you'll need to do for your changes to take effect. (See main/refresh
;; below for more details.)
;;
;; The `clj -M:dev dev` command also starts an nREPL server on port 7888, so if you're
;; already familiar with REPL-driven development, you can connect to that with your editor.
;;
;; If you're used to jacking in with your editor first and then starting your app via the
;; REPL, you will need to instead connect your editor to the nREPL server that `clj -M:dev
;; dev` starts. e.g. if you use emacs, instead of running `cider-jack-in`, you would run
;; `cider-connect`. See "Connecting to a Running nREPL Server:"
;; https://docs.cider.mx/cider/basics/up_and_running.html#connect-to-a-running-nrepl-server
;; ----------------------------------------------------------------------------------------

;; This function should only be used from the REPL. Regular application code
;; should receive the system map from the parent Biff component. For example,
;; the use-jetty component merges the system map into incoming Ring requests.
(defn get-context []
  (biff/merge-context @main/system))

(defn add-fixtures []
  (let [{:keys [example/ds] :as ctx} (get-context)
        user-id (random-uuid)]
    (jdbc/execute! ds ["INSERT INTO users (id, email, foo) VALUES (?, ?, ?)"
                       user-id "a@example.com" "Some Value"])
    (jdbc/execute! ds ["INSERT INTO message (id, user_id, text) VALUES (?, ?, ?)"
                       (random-uuid) user-id "hello there"])))

(defn reset-db! []
  (let [{:keys [example/ds]} (get-context)]
    (jdbc/execute! ds [(str/join
                        " ; "
                        (for [table ["migrations"
                                     "users"
                                     "message"
                                     "auth_code"]]
                          (str "DROP TABLE IF EXISTS " table)))])
    (jdbc/execute! ds [(slurp (io/resource "migrations.sql"))])))

(defn check-config []
  (let [prod-config (biff/use-aero-config {:biff.config/profile "prod"})
        dev-config  (biff/use-aero-config {:biff.config/profile "dev"})
        ;; Add keys for any other secrets you've added to resources/config.edn
        secret-keys [:biff.middleware/cookie-secret
                     :biff/jwt-secret
                     :postmark/api-key
                     :recaptcha/secret-key
                     ; ...
                     ]
        get-secrets (fn [{:keys [biff/secret] :as config}]
                      (into {}
                            (map (fn [k]
                                   [k (secret k)]))
                            secret-keys))]
    {:prod-config prod-config
     :dev-config dev-config
     :prod-secrets (get-secrets prod-config)
     :dev-secrets (get-secrets dev-config)}))

(comment
  ;; Call this function if you make a change to main/initial-system,
  ;; main/components, :tasks, :queues, config.env, or deps.edn.
  (main/refresh)

  ;; Call this in dev if you'd like to add some seed data to your database. If
  ;; you edit the seed data, you can reset the database by calling reset-db!
  ;; (DON'T do that in prod) and calling add-fixtures again.
  (reset-db!)
  (add-fixtures)

  ;; Create a user
  (let [{:keys [example/ds] :as ctx} (get-context)]
    (jdbc/execute! ds (util-pg/new-user-statement "hello@example.com")))

  ;; Query the database
  (let [{:keys [example/ds] :as ctx} (get-context)]
    (jdbc/execute! ds ["SELECT * FROM users"]))

  ;; Update an existing user's email address
  (let [{:keys [example/ds] :as ctx} (get-context)]
    (jdbc/execute! ds ["UPDATE users SET email = ? WHERE email = ?"
                       "new.address@example.com"
                       "hello@example.com"]))

  (sort (keys (get-context)))

  ;; Check the terminal for output.
  (biff/submit-job (get-context) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-context) :echo {:foo "bar"})))
