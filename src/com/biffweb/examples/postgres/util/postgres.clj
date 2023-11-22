(ns com.biffweb.examples.postgres.util.postgres
  (:require [next.jdbc :as jdbc]))

(defn execute-all! [{:keys [example/ds]} statements]
  (when (not-empty statements)
    (jdbc/with-transaction [tx ds]
      (doseq [statement statements]
        (jdbc/execute! tx statement)))))

(defn new-user-statement [email]
  [(str "INSERT INTO users (id, email) VALUES (?, ?) "
        "ON CONFLICT (email) DO NOTHING")
   (random-uuid) email])

(defn get-user-id [{:keys [example/ds]} email]
  (-> (jdbc/execute! ds ["SELECT id FROM users WHERE email = ?" email])
      first
      :users/id))
