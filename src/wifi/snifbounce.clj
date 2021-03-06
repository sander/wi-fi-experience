(ns wifi.snifbounce
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [wifi.sniffer :as sn]
            [wifi.haptic2 :as haptic]
            [clojure.core.async :refer [<! <!! timeout chan pipe go go-loop] :as async]))

(def running (atom false))

(defn setup []
  (let [s (sn/sniff)]
    (reset! running true)
    {:bounces-per-second 0
     :lower              false
     :sniffer            s
     :data-counter       (-> s sn/data sn/counter)}))

(def padding 20)
(def max-bounces 100)

(defn update [{:keys [] :as state}]
  (assoc state
         :bounces-per-second (q/constrain-int (q/map-range (q/mouse-x) padding (- (q/width) padding) 0 max-bounces) 0 max-bounces)
         :lower (if (q/key-pressed?) 20 0)))

;; TODO base bounces-per-second on data-counter.
;; can be done by calculating packets-per-second and multiplying.

(defn draw [{:keys [bounces-per-second lower data-counter] :as state}]
  (comment (if lower
             (haptic/adjust-motors 30 30)
             (haptic/adjust-motors 0 0)))
  (if (> bounces-per-second 0)
    (haptic/bounce! 5 lower 50 (/ 1000 bounces-per-second)))
  (q/background 255)
  (q/fill 0)
  (q/text (str "Bounces per second: " bounces-per-second) 12 24)
  (q/text (str "Lower: " lower) 12 (+ 24 18))
  (q/text (str "Total data packets: " @data-counter) 12 (+ 24 (* 2 18)))
  state)

(defn on-close [{:keys [] :as state}]
  (reset! running false))

(q/defsketch bounce
             :setup setup
             :update update
             :draw draw
             :on-close on-close
             :size [300 200]
             :middleware [m/fun-mode])