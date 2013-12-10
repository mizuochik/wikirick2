(ns wikirick2.handler
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as response]
            [slingshot.slingshot :refer :all]
            [wikirick2.screen :as screen]
            [wikirick2.service :refer :all]
            [wikirick2.types :refer :all]))

(defn- open-read-view [{title :title revision :rev}]
  (with-wiki-service
    (try+
      (let [page (select-page storage title)]
        (read-view screen page (when revision (Integer/parseInt revision))))
      (catch [:type :page-not-found] _
        (response/redirect (page-action-path url-mapper title "new"))))))

(defn- open-new-view [title]
  (with-wiki-service
    (new-view screen (assoc (new-page storage title) :source "new content"))))

(defn- open-edit-view [title]
  (with-wiki-service
    (try+
      (let [page (select-page storage title)]
        (edit-view screen page))
      (catch [:type :page-not-found] _
        (response/redirect (page-action-path url-mapper title "new"))))))

(defn- open-preview-view [{:keys [title source]}]
  (with-wiki-service
    (let [page (assoc (new-page storage title)
                 :source source)]
      (preview-view screen page))))

(defn- open-search-view [{:keys [word]}]
  (with-wiki-service
    (search-view screen word (search-pages storage word))))

(defn- open-history-view [title]
  (with-wiki-service
    (try+
      (let [page (select-page storage title)]
        (history-view screen page))
      (catch [:type :page-not-found] _
        (response/redirect (page-action-path url-mapper title "new"))))))

(defn- open-diff-view [{title :title revision-range :range}]
  (with-wiki-service
    (if-let [[_ from-rev to-rev] (re-matches #"(\d+)-(\d+)" revision-range)]
      (diff-view screen
                 (select-page storage title)
                 (Integer/parseInt from-rev)
                 (Integer/parseInt to-rev)))))

(defn- register-new-page [{:keys [title source]}]
  (with-wiki-service
    (save-page (assoc (new-page storage title) :source source))))

(defn- update-page [{:keys [title source]}]
  (with-wiki-service
    (save-page (assoc (new-page storage title)
                 :source source))
    (response/redirect-after-post (page-path url-mapper title))))

(defn- catch-known-exceptions [app]
  (fn [req]
    (try+
      (app req)
      (catch [:type :invalid-page-title] _
        ((route/not-found "Not Found") req)))))

(def wikirick-routes
  (-> (routes (GET "/" {params :params} (open-read-view (assoc params :title "FrontPage")))
              (GET "/w/:title" {params :params} (open-read-view params))
              (GET "/w/:title/new" [title] (open-new-view title))
              (POST "/w/:title/new" {params :params} (register-new-page params))
              (GET "/w/:title/edit" [title] (open-edit-view title))
              (POST "/w/:title/preview" {params :params} (open-preview-view params))
              (POST "/w/:title/edit" {params :params} (update-page params))
              (GET "/w/:title/diff/:range" {params :params} (open-diff-view params))
              (GET "/w/:title/history" [title] (open-history-view title))
              (GET "/search" {params :params} (open-search-view params))
              (route/resources "/")
              (route/not-found "Not Found"))
      catch-known-exceptions))
