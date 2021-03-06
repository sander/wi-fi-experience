(ns wifi.haptic
  (:require [serial-port :as serial]
            ;[quil.core :as q]
            [clojure.core.async :as async :refer [chan alts! go go-loop timeout <! >! put! sliding-buffer]]))

(defn open
  "Opens the connection to Arduino."
  ([path] (serial/open path))
  ([] (open "/dev/tty.usbmodem1411" #_ "/dev/tty.usbmodem1421")))
; 1411 is right, 1421 left

(defonce port (atom (open)))

(defn close
  "Closes the connection to Arduino."
  []
  (serial/close @port))

(defn reopen
  "Creates a new connection to Arduino."
  []
  (reset! port (open)))

(def last-motor-status (atom [0 0]))

(defn set-motors
  "Sets the motor positions to m1 and m2."
  [m1 m2]
  (reset! last-motor-status [m1 m2])
  (serial/write @port (byte-array [255 m1 m2])))

(defn manual
  "Switches to manual control mode using potmeters."
  []
  (reset! last-motor-status [0 0])
  (serial/write @port (byte-array [254])))

(defn listen
  "Listens to touch events from Arduino and puts :press or :release on the return channel.
  NOT USEABLE ANYMORE!"
  []
  (let [ch (chan)
        status (atom 0)]
    (serial/on-byte @port (fn [st]
                            (when (not= @status st)
                              (case st
                                1 (put! ch :release)
                                2 (put! ch :press)
                                nil))
                            (reset! status st)))
    ch))

(defn interpret
  [s]
  (if (> s 127)
    [true (- s 128)]
    [false s]))
(defn listen2
  []
  (let [touch (chan (sliding-buffer 1))
        dist (chan (sliding-buffer 1))
        touch-status (atom 0)
        dist-status (atom 0)]
    (serial/on-byte @port (fn [st]
                            (let [[t d] (interpret st)]
                              (when (not= @touch-status t)
                                (println "\ttouch" t)
                                (put! touch (if (reset! touch-status t) :press :release)))
                              (when (not= @dist-status d)
                                (println "dist" d)
                                (put! dist (reset! dist-status d))))))
    [touch dist]))

(defn listen3
  []
  (let [touch (chan (sliding-buffer 1))
        pressure (chan (sliding-buffer 1))
        dist (chan (sliding-buffer 1))
        touch-status (atom 0)
        pressure-status (atom 0)
        dist-status (atom 0)
        current (atom :touch)]
    (serial/on-byte @port (fn [b]
                            (if (= b 0)
                              (do
                                ;(println "reset")
                                (reset! current :touch))
                              (do
                                ;(println "val" @current b)
                                ;; it is a value
                                (case @current
                                  :touch
                                  (put! touch (reset! touch-status b))

                                  :pressure
                                  (put! pressure (reset! pressure-status b))

                                  :dist
                                  (put! dist (reset! dist-status b)))
                                (swap! current #(case %
                                                 :touch :pressure
                                                 :pressure :dist
                                                 :dist :touch))
                                (comment
                                  (if (= @current :touch)
                                    (if true                ;(not= @touch-status b)
                                      (put! touch (reset! touch-status b)))
                                    (if true                ;(not= @dist-status b)
                                      (put! dist (reset! dist-status b))))
                                  (swap! current #(if (= % :touch) :dist :touch)))))))
    [touch pressure dist]))

(defn listen4
  []
  (let [touch (chan (sliding-buffer 1))
        pressure (chan (sliding-buffer 1))
        dist (chan (sliding-buffer 1))
        touch-status (atom 0)
        pressure-status (atom 0)
        dist-status (atom 0)
        current (atom :touch)
        last (atom nil)]
    (comment)
    (serial/on-byte @port (fn [b]
                            (if (= b 0)
                              (do
                                ;(println "reset")
                                (reset! current :touch))
                              (do
                                ;(reset! last [@current b])
                                ;(println "val" @current b)
                                (if (and (= @current :pressure) (> b 2))
                                  (println "val" @current b))
                                ;; it is a value
                                (case @current
                                  :touch
                                  (put! touch (reset! touch-status b))

                                  :pressure
                                  (put! pressure (reset! pressure-status b))

                                  :dist
                                  (put! dist (reset! dist-status b)))
                                (swap! current #(case %
                                                 :touch :pressure
                                                 :pressure :dist
                                                 :dist :touch))))))
    [touch-status pressure-status dist-status last]))

(defn indexes-of [e coll] (keep-indexed #(if (= e %2) %1) coll))
(defn next-in [e cycle] (nth cycle (inc (first (indexes-of e cycle)))))

(defn create-sensor-channel [] (chan (sliding-buffer 1)))
(defn listen5
  []
  (let [sensors [:touch :pressure :dist]
        order (cycle sensors)
        state (atom {:current (first sensors)})
        output (zipmap sensors (for [_ sensors] (create-sensor-channel)))]
    (serial/on-byte @port (fn [b]
                            (case b
                              0 (swap! state assoc :current (first sensors))
                              (do
                                (put! ((:current @state) output) b)
                                (swap! state #(let [c (:current %)]
                                               (assoc %
                                                      c b
                                                      :current (next-in c order)))))))
                    false)
    output))

(defn listen6 []
  (let [vals [:servo1 :servo2 :pressure]
        order (cycle vals)
        state (atom {:current (first vals)})
        output (zipmap vals (for [_ vals] (create-sensor-channel)))]
    (serial/on-byte @port (fn [b]
                            ;(println "new" b (:current @state))
                            (if (= b 0)
                              (swap! state assoc :current (first vals))
                              (let [curr (:current @state)]
                                (if-not (= (@state curr) b)
                                  (do
                                    (put! (output curr) b)
                                    (swap! state assoc curr b :current (next-in curr order)))))))
                    true)
    output))

(defn unlisten
  "Stops listening to touch events."
  []
  (serial/remove-listener @port))

(defonce vibration-intensity (atom 8))
(defonce vibration-skip (atom 2))
(def vibrating (atom false))
(def vibration-enabled (atom true))
(def vibration-up (atom false))
(def fps 60)
(def vibration-ms (/ 1000 fps))
(def vibration-active (atom false))

(defn should-vibrate?
  []
  (and @vibration-enabled @vibrating))

(defn vibrate
  []
  (when (not @vibration-active)
    (reset! vibration-active true)
    (go-loop
      []
      (when (should-vibrate?)
        (let [add ((if @vibration-up - +) @vibration-intensity)]
          (apply set-motors (map #(+ add %) @last-motor-status)))
        (swap! vibration-up not)
        (<! (timeout vibration-ms)))
      (if @vibrating
        (recur)
        (do
          (comment
            (if (not @vibration-up)
              (apply set-motors (map #(- % @vibration-intensity) @last-motor-status))))
          (reset! vibration-active false))))))

(defn disable-vibration!
  [dur]
  (reset! vibration-enabled false)
  (go
    (<! (timeout dur))
    (reset! vibration-enabled true)))

(defn set-vibrating!
  [v]
  (when (not= @vibrating v)
    (reset! vibrating v)
    (if v (vibrate))))

(defn set-motors2
  [m1 m2]
  (serial/write @port (byte-array [255 m1 m2])))
(defn vibrate2
  []
  (when (not @vibration-active)
    (reset! vibration-active true)
    (go-loop
      []
      (when (should-vibrate?)
        (let [add ((if @vibration-up - +) @vibration-intensity)]
          (apply set-motors2 (take 2 (repeat add))))
        (swap! vibration-up not)
        (<! (timeout vibration-ms)))
      (if @vibrating
        (recur)
        (reset! vibration-active false)))))
(defn set-vibrating2!
  [v]
  (when (not= @vibrating v)
    (reset! vibrating v)
    (if v (vibrate))))