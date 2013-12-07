(ns wikirick2.url-mapper
  (:require [clojure.string :as string]
            [wikirick2.types :refer :all])
  (:import java.net.URLEncoder))

(declare encode concat-paths build-url)

(deftype URLMapper [base-path]
  IURLMapper
  (index-path [self]
    (build-url self [""] nil))

  (page-path [self page-title]
    (build-url self ["w" page-title] nil))

  (page-revision-path [self page-title revision]
    (build-url self ["w" page-title] (str "?rev=" revision)))

  (page-diff-path [self page-title src-rev dest-rev]
    (build-url self
               ["w" page-title "diff" (format "%s-%s" src-rev dest-rev)]
               nil))

  (diff-from-previous-path [self page-title revision]
    {:pre (not= revision 1)}
    (page-diff-path self page-title (dec revision) revision))

  (diff-from-next-path [self page-title revision]
    {:pre (not= revision 1)}
    (page-diff-path self page-title revision (inc revision)))

  (page-action-path [self page-title action-name]
    (build-url self ["w" page-title (.toLowerCase action-name)] nil))

  (theme-path [self]
    (build-url self ["theme.css"] nil))

  (search-path [self]
    (build-url self ["search"] nil)))

(defn- build-url [urls segments query]
  (let [segs (concat (string/split (.base-path urls) #"/") segments)
        encoded-segs (map #(URLEncoder/encode % "UTF-8") segs)]
    (str (string/join "/" encoded-segs) query)))
