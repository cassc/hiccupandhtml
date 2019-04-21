(ns hiccupandhtml.core
  (:require
   [clojure.string :as string]
   [reagent.core :as r :refer [atom]]
   [hickory.render :refer [hiccup-to-html hickory-to-html]]
   [hickory.core :refer [as-hickory as-hiccup parse-fragment parse]]
   [com.rpl.specter :refer [walker]]
   
   [cljs.reader :refer [read-string]]
   [cljsjs.codemirror]
   [cljsjs.codemirror.mode.xml]
   [cljsjs.codemirror.mode.clojure]
   [cljsjs.codemirror.addon.edit.closetag]
   [cljsjs.codemirror.addon.edit.closebrackets]
   [cljsjs.codemirror.addon.edit.matchbrackets])
  (:require-macros
   [com.rpl.specter :as s :refer [transform]]
   [cljs.core.async.macros :refer [go-loop go]]))

(enable-console-print!)

(defonce html-store (atom ""))
(defonce hiccup-store (atom ""))
(defonce app-store (atom {}))


(defn html->hiccup [val snippet?]
  (if snippet?
    (map as-hiccup (parse-fragment val))
    (as-hiccup (parse val))))

(defn- convert-string-style-to-map [xs]
  (transform (walker (fn [el]
                       (and (map? el)
                            (string? (:style el)))))
             (fn [{:keys [style] :as el}]
               (assoc el :style (reduce (fn [m kv]
                                          (let [[k v] (string/split kv #":")]
                                            (if (not (string/blank? k))
                                              (assoc m (string/trim k) (when v (string/trim v)))
                                              m)
                                            ))
                                        {}
                                        (string/split style #";"))))
             xs))

(defn handle-parse [val]
  (-> val
      (html->hiccup true)
      convert-string-style-to-map
      str
      ;; remove outer parens ()
      (string/replace-first #"^\((.*)\)" "$1")
      ;; remove trailing "\n    "
      (string/replace #"\"(\s*\\n\s*(\w)*)*\"" "\"$2\"") 
      ;; remove in string "\n    "
      (string/replace #"\"(\\n\s*)" "\"")
      (string/replace #"(\\n\s*)\"" "\"")
      ;; start every opening [ on new line
      (string/replace #"\[" "\n[")
      ;; ;; remove empty {}
      ;; (string/replace #" \{\}" "")
      ;; ;; remove trailing whitespace and empty strings
      ;; (string/replace #"([\]\}])(\s*(\"\s*\"\s*)*)[\n]" "$1\n")
      ;; remove initial \n
      (string/replace-first #"^\n" "")
      ;; remove whitespace between closing brackets;
      (string/replace #"\]([ \t]+)" "]")))

(defn try-html-to-hiccup! []
  (try
    (let [hic (->> @html-store
                   handle-parse)]
      (reset! hiccup-store hic)
      (swap! app-store dissoc :error))
    #_(catch :default e
      (swap! app-store assoc :error {:e e
                                     :tpe :html
                                     :msg "Please enter valid html form!"})
      (println "Ignore error" e))))

(defn- remove-leading-div [ss]
  (let [n (count ss)]
    (subs ss
          (count "<div>")
          (- n (count "</div>")))))

(defn- to-string [ss]
  (if (keyword? ss)
    (name ss)
    (str ss)))

(defn- convert-reagent-style [xs]
  (transform (walker (fn [el]
                       (and (map? el)
                            (map? (:style el)))))
             (fn [{:keys [style] :as el}]
               (assoc el :style (reduce-kv (fn [ss k v]
                                             (let [s-prop (str (to-string k) ":" (to-string v))]
                                               (str ss "" s-prop (when-not (string/ends-with? s-prop ";") ";"))))
                                           ""
                                           style)))
             xs))

(defn- remove-onclick-function [ss]
  (string/replace ss #":on-click\s+#\(.*\)"
               ":on-click func"))

(def html-beautify-opts
  (clj->js
   {"indent_size" "2"
    "space_in_empty_paren" true
    "wrap_line_length" "80"}))

(defn beautify-html [ss]
  (js/html_beautify ss html-beautify-opts))

(defn try-hiccup-to-html! []
  (try
    (let [hic @hiccup-store
          html (if (string/blank? hic)
                 ""
                 (->> (str "[:div " hic "]")
                      remove-onclick-function
                      (read-string {:eof nil})
                      (conj [])
                      convert-reagent-style
                      hiccup-to-html
                      remove-leading-div
                      beautify-html))]
      (swap! app-store dissoc :error)
      (reset! html-store html))
    (catch :default e
      (swap! app-store assoc :error {:e e
                                     :tpe :clojure
                                     :msg "Please enter valid hiccup form!"})
      (println "Ignore error" e))))

(defn try-convert! [store]
  (println store html-store hiccup-store)
  (if (= store html-store)
    (try-html-to-hiccup!)
    (try-hiccup-to-html!)))

(defn code-mirror
  "Create a code-mirror editor. The parameters:
  value-atom (reagent atom)
    when this changes, the editor will update to reflect it.
  options
  :style (reagent style map)
    will be applied to the container element
  :js-cm-opts
    options passed into the CodeMirror constructor
  :on-cm-init (fn [cm] -> nil)
    called with the CodeMirror instance, for whatever extra fiddling you want to do."
  [value-atom {:keys [style
                      js-cm-opts
                      on-cm-init]}]

  (let [cm (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [el (r/dom-node this)
              inst (js/CodeMirror.
                    el
                    (clj->js
                     (merge
                      {:lineNumbers true
                       :lineWrapping true
                       :matchBrackets true
                       :value @value-atom
                       :autoCloseBrackets true
                       :mode "clojure"}
                      js-cm-opts)))]
          (reset! cm inst)
          (.on inst "change"
               (fn []
                 (let [value (.getValue inst)]
                   (when-not (= value @value-atom)
                     (reset! value-atom value)
                     (try-convert! value-atom)))))
          (when on-cm-init
            (on-cm-init inst))))

      :component-did-update
      (fn [this old-argv]
        (when-not (= @value-atom (.getValue @cm))
          (.setValue @cm @value-atom)
          ;; reset the cursor to the end of the text, if the text was changed externally
          (let [last-line (.lastLine @cm)
                last-ch (count (.getLine @cm last-line))]
            
            (.setCursor @cm last-line last-ch))))

      :reagent-render
      (fn [_ _ _]
        @value-atom
        [:div.cm-editor {:style style}])})))

(defn my-app []
  (fn []
    (let [{:keys [tpe e msg]} (:error @app-store)]
      [:div.hiccupandhtml
       [:div.hiccupandhtml__title
        [:h2 "HTML"]
        [:span
         (when (= tpe :html)
           (str msg " " e))]]
       [:div.hiccupandhtml__title
        [:h2 "Hiccup"]
        [:span
         (when (= tpe :clojure)
           (str msg " " e))]]
       [code-mirror html-store {:js-cm-opts {:lineNumbers true
                                             :mode "html"
                                             :indentWithTabs false
                                             :matchBrackets true
                                             :tabSize 2}
                                :on-cm-init (fn [cm]
                                              (.setSize cm nil 500))}]
       [code-mirror hiccup-store {:js-cm-opts {:lineNumbers true
                                               :mode "clojure"
                                               :indentWithTabs false
                                               :matchBrackets true
                                               :tabSize 2}
                                  :on-cm-init (fn [cm]
                                                (.setSize cm nil 500))}]])))

(defn main []
  (r/render [#'my-app] (.getElementById js/document "app")))

(main)

