(ns wikirick2.handler
  (:use compojure.core
        wikirick2.service
        wikirick2.types)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [wikirick2.screen :as screen]))

(def ^:dynamic wiki-service
  (->WikiService
   {:repository-dir "repo"
    :base-path "/"}))

(defn- service [getter]
  (getter wiki-service))

(defn- handle-route []
  "<h1>Hello World</h1>")

(defn- handle-navigation [])

(defn- handle-article [title]
  (let [article (select-article (service get-repository) title)]
    (render-full (service get-screen) (screen/article article))))

(defroutes app-routes
  (GET "/" [] (handle-route))
  (GET "/:title" [title] (handle-article title))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
