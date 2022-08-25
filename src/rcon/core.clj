(ns rcon.core
  (:require [aleph.tcp :as tcp]
            [org.clojars.smee.binary.core :as b]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [rcon.codecs :as codecs]))

(defn- encode->bytes [codec data]
  (let [bao (java.io.ByteArrayOutputStream.)]
    (b/encode codec bao data)
    (.toByteArray bao)))

(defn- bytes->decode [codec data]
  (b/decode codec (java.io.ByteArrayInputStream. data)))

(defn- encode-rcon [data]
  (encode->bytes codecs/packet-codec data))

(defn- decode-rcon [data]
  (bytes->decode codecs/packet-codec data))

(defn- split-frames
  "A transducer which accepts packets and concatenates/splits them
  into individual rcon messages."
  [rf]
  (let [reading-len (volatile! true)
        target-len (volatile! -1)
        a (java.util.ArrayList. 4096)]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (.add a input)
       (cond
         (and @reading-len (= 4 (.size a)))
         (let [header (byte-array (.toArray a))
               frame-len (bytes->decode codecs/framing-codec header)]
           (vreset! reading-len false)
           (vreset! target-len frame-len)
           (.clear a)
           result)

         (and (not @reading-len) (= @target-len (.size a)))
         (let [frame (byte-array (.toArray a))]
           (vreset! reading-len true)
           (vreset! target-len -1)
           (.clear a)
           (rf result frame))

         :else result)))))

(defn- wrap-rcon [s]
  (let [wrapped (s/stream)]
    (s/connect (s/map encode-rcon wrapped) s)
    (s/splice wrapped (s/map decode-rcon (s/transform (comp cat split-frames) s)))))

(defn connect
  "Connects to a Minecraft RCON server at host on port, identified by
  password. May throw an IllegalStateException or ConnectException if
  unable to connect (depending on the cause).
  Returns a manifold stream."
  [host port password]
  (let [conn (tcp/client {:host host :port port})
        auth-req-id 0
        auth (codecs/auth auth-req-id password)
        send-auth! #(do (s/put! % auth) %)
        recv-auth-response!
        (fn [rcon]
          (let [recv! #(deref (s/try-take! rcon 3000))
                auth-response (recv!)]
            (println "auth-response: " auth-response)
            (if (and (= (:type auth-response) codecs/serverdata-auth-response)
                     (= (:id auth-response) auth-req-id))
              rcon
              (if (nil? auth-response)
                (throw (Exception. "timeout"))
                (throw (Exception. "bad password"))))))]
    (d/chain
      (d/catch @(d/chain conn wrap-rcon send-auth!)
               d/error-deferred)
      recv-auth-response!)))

(defn exec [connection cmd]
  (let [req-id (rand-int 0x7fffffff)
        response (d/deferred)]
    (s/put! connection (codecs/exec req-id cmd))
    (d/timeout! response 2000 :timeout)
    (future []
            (d/success! response
                        (:body @(s/take! (s/filter #(= (:id %) req-id)
                                                   connection)))))
    response))

(defn ^BigInteger bytes->int
  [^bytes bytes & {:keys [little-endian]
                   :or   {little-endian true}}]
  (let [b (if little-endian (reverse bytes) bytes)]
    (->> b
         (cons (byte 0))
         (byte-array)
         (biginteger))))

(defn ^bytes int->bytes
  [number & {:keys [little-endian len]
             :or   {little-endian true
                    len           4}}]
  (let [buffer (loop [buf (vec (repeat len number))
                      i 0]
                 (if (< i (count buf))
                   (recur (update buf i #(bit-shift-right (bit-and (bit-shift-left 0xff (* 8 i)) %) (* 8 i))) (inc i))
                   (byte-array buf)))]
    (if-not little-endian
      (reverse buffer)
      buffer)))

(defn build-rcon
  [id protocol content]
  (let [cmd (-> (str content) (.getBytes "UTF-8"))]
    (byte-array (concat (vec (int->bytes (+ 10 (count cmd))))
                        (vec (int->bytes (int id)))
                        (vec (int->bytes protocol))
                        (vec cmd)
                        (vec (int->bytes 0 {:len 2}))))
    ))

(comment

  (cons (byte 0) (byte-array 1))
  (def c (connect "127.0.0.1" 25575 "123456"))
  (deref c)
  (deref (exec @c "kill @e[type=item]"))
  (deref (exec @c "say hello from rcon."))
  (.close @c)


  (b/c-string "UTF-8")

  (def client (aleph.tcp/client {:host "192.168.1.2"
                                 :port 25575}))

  (require '[manifold.stream :as s])
  (deref client)
  (bytes->decode codecs/packet-codec @(s/take! @client))
  @(s/put! @client (build-rcon 0 codecs/serverdata-auth "12345"))
  @(s/put! @client (build-rcon 22 codecs/serverdata-exec "forge tps"))



  (let [cmd (-> "12345" .getBytes)
        protocol 3]
    (prn (vec cmd))
    (prn (concat (vec (int->bytes (+ 10 (count cmd))))
                 (vec (int->bytes 1))
                 (vec (int->bytes protocol))
                 (vec cmd)
                 (vec (int->bytes 0 {:len 2}))))
    )



  (update (vec (repeat 4 564654)) 0 inc)

  (bit-shift-left 0xff 8)
  (bit-shift-right (bit-and (bit-shift-left 0xff 8) 256) 8)

  (-> (loop [buf (vec (repeat 4 2456456))
             i 0]
        (if (< i (count buf))
          (recur (update buf i #(bit-shift-right (bit-and (bit-shift-left 0xff (* 8 i)) %) (* 8 i))) (inc i))
          buf))
      byte-array
      bytes->int
      )

  (-> 45646512
      int->bytes
      bytes->int)

  )