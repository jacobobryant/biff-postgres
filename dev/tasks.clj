(ns tasks
  (:require [com.biffweb.task-runner :refer [run-task]]
            [com.biffweb.tasks :as tasks]
            [com.biffweb.tasks.lazy.babashka.fs :as fs]
            [com.biffweb.tasks.lazy.babashka.process :refer [shell]]
            [com.biffweb.tasks.lazy.clojure.java.io :as io]
            [com.biffweb.tasks.lazy.com.biffweb.config :as config]))


(def config (delay (config/use-aero-config {:biff.config/skip-validation true})))

(defn hello
  "Says 'Hello'"
  []
  (println "Hello"))

(defn start-postgres []
  (try
    (shell {:out nil} "docker inspect postgres")
    (catch Exception e
      (shell "docker pull postgres")))
  (apply shell
         (concat ["docker" "run" "--rm"]
                 (:example/docker-postgres-args @config)
                 ["-v" (str (.getAbsolutePath (io/file "storage/postgres"))
                            ":/var/lib/postgresql/data")
                  "postgres"])))

(defn dev
  "Starts the app locally.

   After running, wait for the `System started` message. Connect your editor to
   nrepl port 7888 (by default). Whenever you save a file, Biff will:

   - Evaluate any changed Clojure files
   - Regenerate static HTML and CSS files
   - Run tests"
  []
  (if-not (fs/exists? "target/resources")
    ;; This is an awful hack. We have to run the app in a new process, otherwise
    ;; target/resources won't be included in the classpath. Downside of not
    ;; using bb tasks anymore -- no longer have a lightweight parent process
    ;; that can create the directory before starting the JVM.
    (do
      (io/make-parents "target/resources/_")
      (shell "clj" "-M:dev" "dev"))
    (let [{:keys [biff.tasks/main-ns biff.nrepl/port] :as ctx} @config]
      (when-not (fs/which "docker")
        (println "The `docker` command could not be found. Please make sure docker is installed.")
        (System/exit 1))
      (when-not (fs/exists? "config.env")
        (run-task "generate-config"))
      (when (fs/exists? "package.json")
        (shell "npm install"))
      (future (run-task "css" "--watch"))
      (future (start-postgres))
      (spit ".nrepl-port" port)
      ((requiring-resolve (symbol (str main-ns) "-main"))))))

;; Tasks should be vars (#'hello instead of hello) so that `clj -M:dev help` can
;; print their docstrings.
(def custom-tasks
  {"hello" #'hello
   "dev"   #'dev})

(def tasks (merge tasks/tasks custom-tasks))
