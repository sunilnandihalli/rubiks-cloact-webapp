(ns rubiks-cloact-webapp.cube
  (:require [clojure.set]))

(defn cartesian-product
  ([] [])
  ([x] (for [xi x] [xi]))
  ([x y] (for [xi x yi y] [xi yi]))
  ([x y z] (for [xi x yi y zi z] [xi yi zi])))

(defn combinations [[x & xs] n]
  (cond
   (= n 0) [[]]
   (nil? x) []
   :default (concat (map #(cons x %) (combinations xs (dec n)))
                    (combinations xs n))))

(defn coord-map-to-coord-vec [{:keys [x y z] :or {x 0 y 0 z 0}}] [x y z])
(defn cube-geometry []
  (let [dirs [:x :y :z]
        dir-pairs [[[:x :y] :z] [[:y :z] :x] [[:z :x] :y]]

        cvals [0 1]
        dir-faces (fn [[[d1 d2] d3]]
                    (let [[v00 v01 v10 v11] (for [c-v1 cvals c-v2 cvals] {d1 c-v1 d2 c-v2})
                          f-coords [v00 v10 v11 v01]
                          positive-face (mapv #(assoc % d3 (cvals 1)) (rseq f-coords))
                          negative-face (mapv #(assoc % d3 (cvals 0)) f-coords)]

                      [d3 {:positive-face positive-face :negative-face negative-face}]))
        faces (into {} (map dir-faces dir-pairs))]
    faces))

;; [step [:x|:y|:z 0|...|n-1 :clockwise|:counter-clockwise]]
(def ^:dynamic *rotation-op* nil)

(defn cube-piece
  ([]
     (let [cgeom (cube-geometry)
           color {:x {:min :red :max :green}
                  :y {:min :blue :max :yellow}
                  :z {:min :orange :max :white}}]
       {:position {}
        :faces (mapcat (fn [dir]
                         (let [{:keys [positive-face negative-face]} (cgeom dir)
                               {:keys [min max]} (color dir)]
                           [{:face positive-face :normal {dir 1} :color max} {:face negative-face :normal {dir -1} :color min}]))
                       [:x :y :z])}))
  ([{[x] :x [y] :y [z] :z :as p} n]
                    (let [n-1 (- n 1)
                          cgeom (cube-geometry)]
                      {:position {:x x :y y :z z}
                       :faces (mapcat
                               (fn [[dir [dir-coord dir-color]]]
                                 (let [{:keys [positive-face negative-face]} (cgeom dir)]
                                   [(into {:face positive-face :normal {dir 1}} (if (= dir-coord n-1) [[:color dir-color]]))
                                    (into {:face negative-face :normal {dir -1}} (if (= dir-coord 0) [[:color dir-color]]))])) p)})))

(defn rubiks-cube-geometry [{:keys [pieces n] :as rcs}]
  (mapv #(cube-piece % n) pieces))
#_ (def rcs (rubiks-cube-nxnxn 3))
#_ (def rcs-geom (rubiks-cube-geometry rcs))
(def dirs [:x :y :z])
(def sides [[:green :blue] [:white :yellow] [:red :orange]])

(defn rubiks-cube-nxnxn [n]
  (let [pairs-to-map #(into {} %)
        boundary-sides (map (fn [dir [neg-color pos-color]] [[dir [0 neg-color]] [dir [(dec n) pos-color]]]) dirs sides)
        corner-pieces (apply cartesian-product boundary-sides)
        edge-pieces (if (> n 2) (mapcat #(apply cartesian-product %) (combinations boundary-sides 2)))
        face-pieces (if (> n 2) (map vector (apply concat boundary-sides)))
        dirs-set (set dirs)
        general-pieces (concat corner-pieces edge-pieces face-pieces)]
    {:n n
     :pieces (mapcat (fn [general-piece]
                       (let [bound-dirs (map first general-piece)
                             dirs-to-iter (apply disj dirs-set bound-dirs)
                             vec-range (map vector (range 1 (dec n)))
                             general-piece-map (pairs-to-map general-piece)
                             piece-variables-before-cp (map (fn [dir]
                                                              (mapv #(vector dir %)
                                                                    vec-range))
                                                            dirs-to-iter)
                             piece-variables (or (seq (apply cartesian-product piece-variables-before-cp)) [[]])
                             ret (map #(into general-piece-map %) piece-variables)]
                         ret))
                     general-pieces)}))

;; ops = seq of [:x|:y|:z id :clockwise|:counter-clockwise] rotation direction always with respect to positive directions
(let [transformer (fn transformer [dir ort n]
                    (let [trf (let [n-1 (- n 1) same identity negate #(- n-1 %)
                                    dirs [:x :y :z]
                                    [_ d1 d2] (drop-while #(not= dir %) (cycle (if (= ort :counter-clockwise) dirs (rseq dirs) )))]
                                {dir [dir same] d1 [d2 same] d2 [d1 negate]})]
                      (fn [rp-dir rp-coord]
                        (let [[n-rp-dir n-rp-coord-fn] (trf rp-dir)]
                          [n-rp-dir (n-rp-coord-fn rp-coord)]))))]
  (defn apply-algorithm [{:keys [pieces n] :as rcs} ops]
    (let [rotate (fn rotate [cur-pieces op]
                   (let [[dir coord ort] op
                         trf (transformer dir ort n)]
                     (map (fn [rubiks-piece]
                            (if-not (= coord (first (rubiks-piece dir))) rubiks-piece
                                    (into {} (map (fn [[rp-dir [rp-coord rp-color] :as orig]]
                                                    (let [[trfed-rp-dir trfed-rp-coord] (trf rp-dir rp-coord)]
                                                      (if-not rp-color [trfed-rp-dir [trfed-rp-coord]]
                                                              [trfed-rp-dir [trfed-rp-coord rp-color]])))
                                                  rubiks-piece))))
                          cur-pieces)))]
      {:n n :pieces (reduce rotate pieces ops)})))

(defn shuffle-rubiks-cube [{:keys [pieces n] :as rcs} & {:keys [num-shuffles] :or {num-shuffles 100}}]
  (apply-algorithm rcs (repeatedly num-shuffles #(vector (rand-nth [:x :y :z]) (rand-int n) (rand-nth [:clockwise :counter-clockwise])))))

(let [faces [:front :back :right :left :top :bottom]]
  (defn face-representation [{:keys [n pieces] :as rcs}]
    (let [n-1 (- n 1)
          {:keys [front back right left top bottom]} {:front (fn [x y z] [:front (- n-1 y) x])
                                                      :back (fn [x y z] [:back (- n-1 y) (- n-1 x)])
                                                      :right (fn [x y z] [:right (- n-1 y) (- n-1 z)])
                                                      :left (fn [x y z] [:left (- n-1 y) z])
                                                      :top (fn [x y z] [:top z x])
                                                      :bottom (fn [x y z] [:bottom (- n-1 z) x])}
          nxn-array (vec (repeat n (vec (repeat n :none))))
          initial-face-representation (into {} (map vector faces (repeat nxn-array)))
          fchoice (fn [v min-fn max-fn]
                    (cond
                     (= v 0) min-fn
                     (= v n-1) max-fn))
          update-face-representation (fn update-face-representation [cds piece]
                                 (let [{[x x-color] :x [y y-color] :y [z z-color] :z} piece]
                                   (as-> cds ncds
                                         (if-not x-color ncds
                                                 (assoc-in ncds ((fchoice x left right) x y z) x-color))
                                         (if-not y-color ncds
                                                 (assoc-in ncds ((fchoice y bottom top) x y z) y-color))
                                         (if-not z-color ncds
                                                 (assoc-in ncds ((fchoice z back front) x y z) z-color)))))]
      {:n n :faces (reduce update-face-representation initial-face-representation pieces)}))

  (defn display [{:keys [n pieces] :or {n 3}:as rcs}]
    (let [{{:keys [front back right left top bottom] :as frcs} :faces} (face-representation rcs)
          empty (vec (repeat n (vec (repeat n \space))))
          sub-color-keys-with-chars (fn [face] (mapv #(mapv (fn [x] (-> x name first)) %) face))
          [front back right left top bottom] (mapv sub-color-keys-with-chars [front back right left top bottom])
          layout [[empty top empty empty]
                  [left front right back]
                  [empty bottom empty empty]]
          merged (mapcat #(apply (partial map concat) %) layout)
          str-rep (apply str (interpose \newline (map #(apply str (interpose \space %)) merged)))]
      (println str-rep))))
