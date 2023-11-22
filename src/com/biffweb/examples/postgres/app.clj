(ns com.biffweb.examples.postgres.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.biffweb.examples.postgres.middleware :as mid]
            [com.biffweb.examples.postgres.ui :as ui]
            [com.biffweb.examples.postgres.settings :as settings]
            [com.biffweb.examples.postgres.util.postgres :as util-pg]
            [next.jdbc :as jdbc]
            [rum.core :as rum]
            [ring.adapter.jetty9 :as jetty]
            [cheshire.core :as cheshire]))

(defn set-foo [{:keys [example/ds session params] :as ctx}]
  (jdbc/execute! ds ["UPDATE users SET foo = ? WHERE id = ?"
                     (:foo params) (:uid session)])
  {:status 303
   :headers {"location" "/app"}})

(defn bar-form [{:keys [value]}]
  (biff/form
   {:hx-post "/app/set-bar"
    :hx-swap "outerHTML"}
   [:label.block {:for "bar"} "Bar: "
    [:span.font-mono (pr-str value)]]
   [:.h-1]
   [:.flex
    [:input.w-full#bar {:type "text" :name "bar" :value value}]
    [:.w-3]
    [:button.btn {:type "submit"} "Update"]]
   [:.h-1]
   [:.text-sm.text-gray-600
    "This demonstrates updating a value with HTMX."]))

(defn set-bar [{:keys [example/ds session params] :as ctx}]
  (jdbc/execute! ds ["UPDATE users SET bar = ? WHERE id = ?"
                     (:bar params) (:uid session)])
  (biff/render (bar-form {:value (:bar params)})))

(defn ui-message [{:message/keys [text sent_at]}]
  [:.mt-3 {:_ "init send newMessage to #message-header"}
   [:.text-gray-600 (biff/format-date sent_at "dd MMM yyyy HH:mm:ss")]
   [:div text]])

(defn send-message [{:keys [example/ds example/chat-clients session] :as ctx} {:keys [text]}]
  (let [{:keys [text]} (cheshire/parse-string text true)
        message {:message/id (random-uuid)
                 :message/user_id (:uid session)
                 :message/text text
                 :message/sent_at (java.sql.Timestamp. (System/currentTimeMillis))}
        html (rum/render-static-markup
              [:div#messages {:hx-swap-oob "afterbegin"}
               (ui-message message)])]
    (jdbc/execute! ds (into ["INSERT INTO message (id, user_id, text, sent_at) VALUES (?, ?, ?, ?)"]
                            ((juxt :message/id :message/user_id :message/text :message/sent_at) message)))
    (doseq [ws @chat-clients]
      (jetty/send! ws html))))

(defn chat [{:keys [example/ds]}]
  (let [messages (jdbc/execute! ds ["SELECT * FROM message WHERE sent_at >= now() - INTERVAL '10 minutes'"])]
    [:div {:hx-ext "ws" :ws-connect "/app/chat"}
     [:form.mb-0 {:ws-send true
                  :_ "on submit set value of #message to ''"}
      [:label.block {:for "message"} "Write a message"]
      [:.h-1]
      [:textarea.w-full#message {:name "text"}]
      [:.h-1]
      [:.text-sm.text-gray-600
       "Sign in with an incognito window to have a conversation with yourself."]
      [:.h-2]
      [:div [:button.btn {:type "submit"} "Send message"]]]
     [:.h-6]
     [:div#message-header
      {:_ "on newMessage put 'Messages sent in the past 10 minutes:' into me"}
      (if (empty? messages)
        "No messages yet."
        "Messages sent in the past 10 minutes:")]
     [:div#messages
      (map ui-message (sort-by :message/sent_at #(compare %2 %1) messages))]]))

(defn app [{:keys [session example/ds] :as ctx}]
  (let [{:users/keys [email foo bar]} (jdbc/execute-one! ds ["SELECT * FROM users WHERE id = ?"
                                                             (:uid session)])]
    (ui/page
     {}
     [:div "Signed in as " email ". "
      (biff/form
       {:action "/auth/signout"
        :class "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
      "."]
     [:.h-6]
     (biff/form
      {:action "/app/set-foo"}
      [:label.block {:for "foo"} "Foo: "
       [:span.font-mono (pr-str foo)]]
      [:.h-1]
      [:.flex
       [:input.w-full#foo {:type "text" :name "foo" :value foo}]
       [:.w-3]
       [:button.btn {:type "submit"} "Update"]]
      [:.h-1]
      [:.text-sm.text-gray-600
       "This demonstrates updating a value with a plain old form."])
     [:.h-6]
     (bar-form {:value bar})
     [:.h-6]
     (chat ctx))))

(defn ws-handler [{:keys [example/chat-clients] :as ctx}]
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! chat-clients conj ws))
        :on-text (fn [ws text-message]
                   (send-message ctx {:ws ws :text text-message}))
        :on-close (fn [ws status-code reason]
                    (swap! chat-clients disj ws))}})

(def about-page
  (ui/page
   {:base/title (str "About " settings/app-name)}
   [:p "This app was made with "
    [:a.link {:href "https://biffweb.com"} "Biff"] "."]))

(defn echo [{:keys [params]}]
  {:status 200
   :headers {"content-type" "application/json"}
   :body params})

(def plugin
  {:static {"/about/" about-page}
   :routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]
            ["/set-foo" {:post set-foo}]
            ["/set-bar" {:post set-bar}]
            ["/chat" {:get ws-handler}]]
   :api-routes [["/api/echo" {:post echo}]]})
