(ns com.biffweb.examples.postgres.tasks
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [babashka.tasks :as tasks :refer [clojure]]
            [com.biffweb.tasks :as biff-tasks]))

(defn start-postgres []
  (when (not= 0 (:exit (tasks/shell {:out nil} "docker inspect postgres")))
    (tasks/shell "docker pull postgres"))
  (apply tasks/shell
         (concat ["docker" "run" "--rm"]
                 (:example/docker-postgres-args @biff-tasks/config)
                 ["-v" (str (.getAbsolutePath (io/file "storage/postgres"))
                            ":/var/lib/postgresql/data")
                  "postgres"])))

(defn dev
  "Starts the app locally. Modified version of com.biffweb.tasks/dev that also
  starts up a Postgres docker container.

  After running, wait for the `System started` message. Connect your editor to
  nrepl port 7888. Whenever you save a file, Biff will:

   - Evaluate any changed Clojure files
   - Regenerate static HTML and CSS files
   - Run tests"
  [& args]
  (when-not (fs/which "docker")
    (println "The `docker` command could not be found. Please make sure docker is installed.")
    (System/exit 1))
  (io/make-parents "target/resources/_")
  (when (fs/exists? "package.json")
    (tasks/shell "npm" "install"))
  (biff-tasks/future-verbose (biff-tasks/css "--watch"))
  (biff-tasks/future-verbose (start-postgres))
  (spit ".nrepl-port" "7888")
  (apply clojure {:extra-env (merge (biff-tasks/secrets) {"BIFF_ENV" "dev"})}
         (concat args (biff-tasks/run-args))))
