(ns wikirick2.repository
  (:use slingshot.slingshot
        wikirick2.helper.repository
        wikirick2.types)
  (:require [clojure.java.shell :as shell]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.jdbc.ddl :as ddl]
            [clojure.java.jdbc.sql :as sql]
            [clojure.string :as string]
            [wikirick2.parsers :as parsers])
  (:import java.sql.SQLException
           java.util.concurrent.locks.ReentrantReadWriteLock))

(declare ->Page)

(deftype Repository [base-dir db rw-lock]
  IRepository
  (select-page [self title]
    (validate-page-title title)
    (with-rw-lock readLock
      (select-page- self title ["-p" title])))

  (select-page-by-revision [self title rev]
    (validate-page-title title)
    (with-rw-lock readLock
      (select-page- self title [(format "-r1.%s" rev) "-p" title])))

  (select-all-page-titles [self]
    (letfn [(ls-rcs-dir []
              (let [result (shell/sh "ls" "-t" (format "%s/RCS" base-dir))]
                (string/split-lines (:out result))))]
      (with-rw-lock readLock
        (let [rcs-files (filter #(not (empty? %)) (ls-rcs-dir))]
          (map #(second (re-find #"(.+),v" %)) rcs-files)))))

  (new-page [self title source]
    (validate-page-title title)
    (->Page self rw-lock title source nil nil)))

(defrecord Page [repo rw-lock title source revision edit-comment]
  IPage
  (save-page [self]
    (letfn [(check-out-rcs-file []
              (shell/sh "co" "-l" title :dir (.base-dir repo)))
            (update-page-relation []
              (let [priority (nlinks-per-page-size self)]
                (jdbc/delete! (.db repo) :page_relation (sql/where {:source title}))
                (doseq [d (referring-titles self)]
                  (jdbc/insert! (.db repo)
                                :page_relation
                                {:source title
                                 :destination d
                                 :priority priority}))))]
      (validate-page-title title)
      (jdbc/db-transaction [db (.db repo)]
        (with-rw-lock writeLock
          (update-page-relation)
          (check-out-rcs-file)
          (let [path (format "%s/%s" (.base-dir repo) title)]
            (spit path source))
          (shell/sh "ci" title :in edit-comment :dir (.base-dir repo))))))

  (referring-titles [self]
    (parsers/scan-wiki-links source))

  (referred-titles [self]
    (jdbc/query (.db repo)
                (sql/select [:source]
                            :page_relation
                            (sql/where {:destination title})
                            "ORDER BY priority DESC")
                :row-fn :source)))

(defn create-repository [base-dir db]
  (letfn [(table-exists? []
            (try
              (jdbc/query db (sql/select * :page_relation))
              true
              (catch SQLException e
                false)))]
    (when (not (table-exists?))
      (jdbc/db-do-commands db
        (ddl/create-table :page_relation
                          [:source "text"]
                          [:destination "text"]
                          [:priority "integer"])
        (ddl/create-index :pr_source_index :page_relation [:source])
        (ddl/create-index :pr_destination_index :page_relation [:destination])
        (ddl/create-index :pr_priority_index :page_relation [:priority])))
    (shell/sh "mkdir" "-p" (format "%s/RCS" base-dir))
    (Repository. base-dir db (ReentrantReadWriteLock.))))
