(ns wifi.core
  (:require [wifi.sniffer :as sn]
            [wifi.sound :as sound]
            ;[quil.core :as q]
            [firmata.core :as firmata]
            [clojure.core.async :as async]
            [clojure.core.async.lab :as async-lab]
            [clojure.data.priority-map :refer [priority-map]])
  (:gen-class))

(defn millis []
  (System/currentTimeMillis))

(defn start-sound []
  (sound/start))

(defn visual [with-arduino with-audio]
  (def running (atom true))
  (def board (if with-arduino
               (firmata.core/open-serial-board "tty.usbmodem1411")))

  (if with-audio
    (start-sound))

  (def sniff (sn/sniff))

  (def total (sn/counter (sn/all sniff)))

  (let [ch (async/chan)]
    (async/sub (:pub sniff) :data ch)
    (def total-data (sn/counter ch)))

  (def lastp (sn/last-item (sn/all sniff)))

  (def interval 1000)
  (def sound-delay (atom interval))
  (def sound-delay-based-on (atom 0))
  (def sound-delay-factor 25)

  (def only-data (async/chan))
  (async/sub (:pub sniff) :data only-data)
  (def ival-count (sn/counter only-data))
  (defn sound-loop []
    (async/<!! (async/timeout interval))
    (reset! sound-delay (if (> @ival-count 0)
                          (int (* sound-delay-factor
                                  (/ interval (double @ival-count))))
                          interval))
    (reset! sound-delay-based-on @ival-count)
    (reset! ival-count 0))
  (async/go (while @running
              (sound-loop)))

  ;; play sounds
  (def color (atom 255))
  (def color-duration 100)
  (defn sound-play-loop []
    (when (not= @sound-delay-based-on 0)
      (reset! color 0)
      (if with-arduino
        (firmata/set-digital board 2 :high))
      (if (> @sound-delay color-duration)
        (async/go
          (async/<! (async/timeout color-duration))
          (reset! color 255)
          (if with-arduino
            (firmata/set-digital board 2 :low))))
      (if with-audio
        (sound/pop-sample)))
    (async/<!! (async/timeout @sound-delay)))
  (async/go (while @running
              (sound-play-loop)))

  (defn stop []
    (reset! running false)
    (async/unsub-all (:pub sniff))
    (if with-arduino
      (firmata/close! board))
    (sn/stop sniff)))

(comment
  (defn sketch []
    (defn setup []
      (q/smooth)
      (q/frame-rate 60)
      (q/background 200))
    (defn draw []
      ;(q/stroke (q/random 255) 0 0)
      ;(q/stroke-weight (q/random 10))
      ;(q/fill 0 0 (q/random 255))
      (q/background 255)
      (q/fill 255)
      (comment
        (q/text (str @counter "/" @total " (" @own-count ")") 0 30)
        (q/text (str @lastp) 0 60)
        (q/text (reduce
                  str
                  (map
                    (fn [[addr count]]
                      (str addr ": " count "\n"))
                    (take 5 (rseq @addresses))))
                0 90))

      (comment
        (q/fill 100)
        (q/push-matrix)
        (q/text-size 18)
        (doall (for [i (range show-lines)]
                 (do
                   (let [l (async/<!! lines-chan)]
                     (q/text (str l) 0 0)
                     (q/translate 0 (/ (q/height) show-lines))))))
        (q/pop-matrix))

      (q/fill @color)
      (let [size 100                                        ;(q/map-range (millis) @anim-start (+ @anim-start anim-duration) 50 200)
            ]
        ;;(println size (millis) @anim-start (+ @anim-start anim-duration))
        (q/ellipse (/ (q/width) 2) (/ (q/height) 2) size size))

      ;; record datas, stream them in the background

      (let [diam (q/random 100)
            ;      x (q/random (q/width))
            ;y (q/random (q/height))
            ]
        ;(q/ellipse x y diam diam)
        ))

    (q/defsketch wifi-sketch
                 :title "Wi-Fi"
                 :setup setup
                 :draw draw
                 :size [1024 768])))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "hello world"))