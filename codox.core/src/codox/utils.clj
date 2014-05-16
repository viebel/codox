(ns codox.utils
  "Miscellaneous utility functions."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(defn- find-minimum [coll]
  (if (seq coll)
    (apply min coll)))

(defn- find-smallest-indent [text]
  (->> (str/split-lines text)
       (remove str/blank?)
       (map #(re-find #"^\s+" %))
       (map count)
       (find-minimum)))

(defn- find-file-in-repo
  "Given a classpath-relative file (as from the output of
   `codox.reader/read-namespaces`), and a sequence of source directory paths,
   returns a File object indicating the file from the repo root."
  [file sources]
  (if (and file (not (.isAbsolute (io/file file))))
    (->> (map #(io/file % file) sources)
         (filter #(.exists %))
         first)))

(defn unindent
  "Unindent a block of text by a specific amount or the smallest common
  indentation size."
  ([text]
     (unindent text (find-smallest-indent text)))
  ([text indent-size]
     (let [re (re-pattern (str "^\\s{0," indent-size "}"))]
       (->> (str/split-lines text)
            (map #(str/replace % re ""))
            (str/join "\n")))))

(defn correct-indent [text]
  (if text
    (let [lines (str/split-lines text)]
      (->> (rest lines)
           (str/join "\n")
           (unindent)
           (str (first lines) "\n")))))

(defn symbol-set
  "Accepts a single item (or a collection of items), converts them to
  symbols and returns them in set form."
  [x]
  (->> (if (coll? x) x [x])
       (filter identity)
       (map symbol)
       (into #{})))

(defn ns-filter
  "Accepts a sequence of namespaces (generated by
  `codox.reader/read-namespaces`), a sequence of namespaces to keep
  and a sequence of namespaces to drop. The sequence is returned with
  all namespaces in `exclude` and all namespaces NOT in `include`
  removed."
  [ns-seq include exclude]
  (let [has-name? (fn [names] (comp (symbol-set names) :name))
        ns-seq    (remove (has-name? exclude) ns-seq)]
    (if include
      (filter (has-name? include) ns-seq)
      ns-seq)))

(defn- unix-path [path]
  (.replace path "\\" "/"))

(defn- normalize-path [path root]
  (let [root (str (unix-path root) "/")
        path (unix-path (.getAbsolutePath (io/file path)))]
    (if (.startsWith path root)
      (.substring path (.length root))
      path)))

(defn add-source-paths
  "Accepts a sequence of namespaces (generated by
   `codox.reader/read-namespaces`), the project root, and a list of
   source directories. The sequence is returned with :path items added
   in each public var's entry in the :publics map, which indicate the
   path to the source file relative to the repo root."
  [ns-seq root sources]
  (let [sources (map #(normalize-path % root) sources)]
    (for [ns ns-seq]
      (assoc ns
        :publics (map #(assoc % :path (find-file-in-repo (:file %) sources))
                      (:publics ns))))))

(defn summary
  "Return the summary of a docstring.
   The summary is the first portion of the string, from the first
   character to the first page break (\f) character OR the first TWO
   newlines."
  [s]
  (if s
    (->> (str/trim s)
         (re-find #"(?s).*?(?=\f)|.*?(?=\n\n)|.*"))))

(defn public-vars
  "Return a list of all public var names in a collection of namespaces from one
  of the reader functions."
  [namespaces]
  (for [ns  namespaces
        var (:publics ns)
        v   (concat [var] (:members var))]
    (symbol (str (:name ns)) (str (:name v)))))

(def ^:private re-chars (set "\\.*+|?()[]{}$^"))

(defn re-escape
  "Escape a string so it can be safely placed in a regex."
  [s]
  (str/escape s #(if (re-chars %) (str \\ %))))

(defn search-vars
  "Find the best-matching var given a partial var string, a list of namespaces,
  and an optional starting namespace."
  [namespaces partial-var & [starting-ns]]
  (let [regex   (if (.contains partial-var "/")
                  (re-pattern (str (re-escape partial-var) "$"))
                  (re-pattern (str "/" (re-escape partial-var) "$")))
        matches (filter
                 #(re-find regex (str %))
                 (public-vars namespaces))]
    (or (first (filter #(= (str starting-ns) (namespace %)) matches))
        (first matches))))
