(ns wikirick2.spec.url-mapper
  (:use wikirick2.url-mapper
        wikirick2.types
        speclj.core))

(def urlm (->URLMapper "/wiki"))

(describe "url mapper"
  (it "expands index pathes"
    (should= "/" (index-path urlm)))

  (it "exapads an article path"
    (let [article (make-article "SomePage" "some content")]
      (should= "/w/SomePage" (article-path urlm article))))

  (it "expands some pathes"
    (should= "/foo" (expand-path urlm "foo")))

  (it "expands the theme path"
    (should= "/theme.css" (theme-path urlm))))
