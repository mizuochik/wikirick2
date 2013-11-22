(ns wikirick2.shell
  (:use slingshot.slingshot
        wikirick2.types)
  (:require [clojure.core.match :refer [match]]
            [clojure.java.shell :as shell]
            [clojure.string :as string]
            [wikirick2.parsers :as parsers]))

(declare parse-co-error rcs-file rcs-dir)

(deftype Shell [base-dir])

(defn co-p [shell title rev]
  (let [result (shell/sh "co" (format "-r1.%s" rev) "-p" title :dir (.base-dir shell))]
    (if (= (:exit result) 0)
      (:out result)
      (throw+ (parse-co-error (:err result))))))

(defn ci [shell title source edit-comment]
  (spit (format "%s/%s" (.base-dir shell) title) source)
  (let [result (shell/sh "ci" "-u" title
                         :in edit-comment
                         :dir (.base-dir shell))]
    (when (not= (:exit result) 0)
      (throw+ {:type :ci-failed}))))

(defn head-version [shell title]
  (let [result (shell/sh "head" (rcs-file title) :dir (rcs-dir shell))
        parse-version #(Integer/parseInt (second (re-find #"head\s+\d+\.(\d+);" %)))]
    (if (= (:exit result) 0)
      (parse-version (first (string/split-lines (:out result))))
      (throw+ {:type :head-version-failed}))))

(defn ls-rcs-files [shell]
  (let [result (shell/sh "ls" "-t" (rcs-dir shell))
        fnames (string/split-lines (:out result))]
    (for [fname fnames :when (not (empty? fname))]
      (second (re-find #"(.+),v" fname)))))

(defn co-l [shell title]
  (shell/sh "co" "-l" title :dir (.base-dir shell)))

(defn make-rcs-dir [shell]
  (shell/sh "mkdir" "-p" (rcs-dir shell)))

(defn test-f [shell title]
  (let [result (shell/sh "test" "-f" (rcs-file title) :dir (rcs-dir shell))]
    (= (:exit result) 0)))

(defn- parse-co-error [error-result]
  (match (re-matches #"co: RCS/(.+),v: (.*)" (.trim error-result))
    [_ page-name "No such file or directory"] {:type :page-not-found}
    err {:type :unknown-error :message (str err)}
    :else (assert false "must not happen: parse-co-error")))

(defn- rcs-file [title]
  (format "%s,v" title))

(defn- rcs-dir [shell]
  (format "%s/RCS" (.base-dir shell)))