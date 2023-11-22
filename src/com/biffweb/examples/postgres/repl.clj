(ns com.biffweb.examples.postgres.repl
  (:require [com.biffweb.examples.postgres :as main]
            [com.biffweb.examples.postgres.util.postgres :as util-pg]
            [com.biffweb :as biff :refer [q]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [next.jdbc :as jdbc]))

;; This function should only be used from the REPL. Regular application code
;; should receive the system map from the parent Biff component. For example,
;; the use-jetty component merges the system map into incoming Ring requests.
(defn get-context []
  (biff/merge-context @main/system))

(defn add-fixtures []
  (let [{:keys [example/ds] :as ctx} (get-context)
        user-id (random-uuid)]
    (jdbc/execute! ds [["INSERT INTO users (id, email, foo) VALUES (?, ?, ?)"
                        user-id "a@example.com" "Some Value"]])
    (jdbc/execute! ds [["INSERT INTO message (id, user_id, text) VALUES (?, ?, ?)"
                        (random-uuid) user-id "hello there"]])))

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

(comment
  ;; Call this function if you make a change to main/initial-system,
  ;; main/components, :tasks, :queues, or config.edn. If you update
  ;; secrets.env, you'll need to restart the app.
  (main/refresh)

  ;; Call this in dev if you'd like to add some seed data to your database. If
  ;; you edit the seed data, you can reset the database by calling reset-db!
  ;; (DON'T do that in prod) and calling add-fixtures again.
  (add-fixtures)

  ;; Query the database
  (let [{:keys [example/ds] :as ctx} (get-context)]
    (jdbc/execute! ds (util-pg/new-user-statement "hello@example.com"))
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
