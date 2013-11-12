(ns wikirick2.parsers
  (:use [hiccup.core :only [h]]
        zetta.core)
  (:require [clojure.string :as string]
            [zetta.combinators :as c]
            [zetta.parser.seq :as s]))

(declare wiki li-level exec-parser)

(defn scan-wiki-links [wiki-source]
  (set (map second (re-seq #"\[\[(.+?)\]\]" wiki-source))))

(defn render-wiki-source [wiki-source]
  (exec-parser wiki (string/split-lines wiki-source)))

(defn- exec-parser [parser lines]
  (or (:result (parse-once parser lines))
      (assert false "exec-parser: must not happen")))

(defn- unlines [lines]
  (string/join "\n" lines))

(defmacro def- [name value]
  (list `def (with-meta name {:private true}) value))

(defn- match-line [regex]
  (fn [input more err-fn ok-fn]
    (let [l (first input)
          matched (and l (re-matches regex l))]
      (if matched
        (ok-fn (rest input) more matched)
        (err-fn input more [] "match-line")))))

(defn- trying [parser]
  (fn [input more err-fn ok-fn]
    (letfn [(err-fn* [_ more* stack msg]
              (err-fn input more* stack msg))]
      (parser input more err-fn* ok-fn))))

(defn- not-followed-by [parser]
  (fn [input more err-fn ok-fn]
    (letfn [(ok-fn* [_ more* result]
              (err-fn input more* [] "not followed by"))

            (err-fn* [_ more* _ _]
              (s/any-token input more* err-fn ok-fn))]
      (parser input more err-fn* ok-fn*))))

(def- empty-line
  (match-line #"\s*"))

(def- atx-header
  (do-parser [[_ syms content] (match-line #"(#{1,6}) *(.*?) *#*")]
    [(keyword (str "h" (count syms))) content]))

(def- settext-header
  (trying (do-parser [content s/any-token
                      [_ underline] (match-line #"(=+|-+) *")]
            (case (first underline)
              \= [:h1 content]
              \- [:h2 content]
              (assert false "must not happen")))))

(def- li-indented-cont-line
  (trying (do-parser [es (c/many empty-line)
                      [_ content] (match-line #" {4}(.+)")]
            (if (empty? es)
              content
              ["" content]))))

(defn- li-cont-lines [li-cont-start]
  (let [no-indented (not-followed-by (<|> li-cont-start empty-line))]
    (c/many (<|> li-indented-cont-line no-indented))))

(defn- list-item [li-start li-cont-start]
  (do-parser [[level l] li-start
              ls (li-cont-lines (li-cont-start level))
              blanks (c/many empty-line)]
    [level (flatten (if (empty? blanks)
                      (cons l ls)
                      (list* "" l ls)))]))

(defn- list-item-cont [li-cont-start]
  (do-parser [[_ l] li-cont-start
              ls (li-cont-lines li-cont-start)
              blanks (c/many empty-line)]
    (flatten (if (empty? blanks)
               (cons l ls)
               (list* "" l ls)))))

(defn- li-plain-lines [li-start]
  (do-parser [lines (c/many1 (not-followed-by li-start))]
    (unlines (map #(.trim %) lines))))

(declare list-parser)

(defn- plain-list [tag-name item-start item-cont-start]
  (c/many (<|> (list-parser tag-name item-start item-cont-start)
               (li-plain-lines item-start))))

(defn- list-parser [tag-name li-start li-cont-start]
  (do-parser [[level ls] (list-item li-start li-cont-start)
              lss (c/many (list-item-cont (li-cont-start level)))
              :let [liness (cons ls lss)
                    extractor (if (empty? (filter #(= % "") (flatten liness)))
                                (plain-list tag-name li-start li-cont-start)
                                wiki)]]
    `[~tag-name ~@(for [lines liness]
                    `[:li ~@(exec-parser extractor lines)])]))

(def- unordered-list
  (list-parser :ul
               (do-parser [[_ spaces content] (match-line #"( {0,3})[\*\+\-]\s+(.*)")]
                 [(count spaces) content])
               #(match-line (re-pattern (format " {%s}[\\*\\+\\-]\\s+(.*)" %)))))

(def- ordered-list
  (list-parser :ol
               (do-parser [[_ spaces content] (match-line #"( {0,3})\d+\.\s+(.*)")]
                 [(count spaces) content])
               #(match-line (re-pattern (format " {%s}\\d+\\.\\s+(.*)" %)))))

(def- code
  (do-parser [:let [code-line (<$> first (match-line #"(\t|    )(.+)"))]
              l code-line
              ls (c/many (<|> code-line empty-line))
              :let [code-lines (cons l ls)
                    trim-left #(.replaceAll % "^(\t|    )" "")
                    trim-right #(.replaceAll % "\\s*$" "")]]
    [:pre [:code (trim-right (unlines (map trim-left code-lines)))]]))

(def- bq-marked-line
  (<$> second (match-line #"\s*> ?(.*)")))

(def- bq-no-marked-line
  (not-followed-by empty-line))

(def- bq-fragment
  (do-parser [l bq-marked-line
              ls (c/many (<|> bq-marked-line bq-no-marked-line))
              _ (c/skip-many empty-line)]
    `(~l ~@ls "")))

(def- blockquote
  (do-parser [fragments (c/many1 bq-fragment)]
    `[:blockquote ~@(exec-parser wiki (apply concat fragments))]))

(def- special
  (reduce <|> [unordered-list
               ordered-list
               code
               atx-header
               settext-header
               blockquote
               empty-line]))

(def- paragraph
  (do-parser [ls (c/many1 (not-followed-by special))]
    [:p (unlines (map #(.trim %) ls))]))

(def- block
  (<|> special paragraph))

(def- wiki
  (do-parser [bs (c/many (*> (c/many empty-line) block))
              _ (c/many empty-line)
              _ s/end-of-input]
    bs))
