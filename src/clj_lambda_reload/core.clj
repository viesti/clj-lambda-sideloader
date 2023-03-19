(ns clj-lambda-reload.core
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.net URL)
           (java.time ZonedDateTime ZoneOffset)
           (java.time.format DateTimeFormatter)
           (java.security MessageDigest)
           (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec)))

(def src-last-modified (atom nil))

(def date-time-format (DateTimeFormatter/ofPattern "yyyyMMdd'T'HHmmss'Z'"))
(def date-format (DateTimeFormatter/ofPattern "yyyyMMdd"))

(defn sha-256 [s]
  (let [md (MessageDigest/getInstance "SHA-256")]
    (.update md (.getBytes s))
    (.digest md)))

(defn- hmac-sha256 [key-bytes data]
  (let [mac (Mac/getInstance "HmacSHA256")]
    (doto mac
      (.init (SecretKeySpec. key-bytes "HmacSHA256"))
      (.update (.getBytes data)))
    (.doFinal mac)))

(defn bytes->hex [bs]
  (str/join (map #(format "%02x" %) bs)))

;; Sign HTTP request as per https://docs.aws.amazon.com/general/latest/gr/create-signed-request.html
(defn open-signed-connection [url]
  (let [access-key-id (System/getenv "AWS_ACCESS_KEY_ID")
        secret-access-key (System/getenv "AWS_SECRET_ACCESS_KEY")
        session-token (System/getenv "AWS_SESSION_TOKEN")
        region (System/getenv "AWS_REGION")

        now (ZonedDateTime/now ZoneOffset/UTC)
        x-amz-date (.format date-time-format now)
        date (.format date-format now)
        service "s3"
        algorithm "AWS4-HMAC-SHA256"
        hashed-payload (bytes->hex (sha-256 ""))
        host (.getHost url)
        headers-to-sign (sort-by first (remove nil?
                                               [["host" host]
                                                ["x-amz-content-sha256" hashed-payload]
                                                ["x-amz-date" x-amz-date]
                                                (when session-token
                                                  ["x-amz-security-token" session-token])]))
        signed-headers (str/join ";" (map first headers-to-sign))
        credential-scope (str/join "/" [date
                                        region
                                        service
                                        "aws4_request"])
        canonical-request (str/join "\n" ["GET" ;; HTTPMethod
                                          (.getPath url) ;; CanonicalUri
                                          "" ;; CanonicalQueryString
                                          (str/join (map (fn [[header value]]
                                                           (str header ":" value "\n"))
                                                         headers-to-sign)) ;; CanonicalHeaders
                                          signed-headers ;; SignedHeaders
                                          hashed-payload ;; HashedPayload
                                          ])
        canonical-request-hash (bytes->hex (sha-256 canonical-request))
        string-to-sign (str/join "\n" [algorithm ;; Algorithm
                                       x-amz-date ;; RequestDateTime
                                       credential-scope ;; CredentialScope
                                       canonical-request-hash])
        signature (-> (.getBytes (str "AWS4" secret-access-key))
                      (hmac-sha256 date)
                      (hmac-sha256 region)
                      (hmac-sha256 service)
                      (hmac-sha256 "aws4_request")
                      (hmac-sha256 string-to-sign)
                      bytes->hex)
        authorization-header (str algorithm " "
                                  "Credential=" access-key-id "/" credential-scope ", "
                                  "SignedHeaders=" signed-headers ", "
                                  "Signature=" signature)
        url-connection (.openConnection url)]
    ;; Prevent caching
    (.setUseCaches url-connection false)
    (.setDefaultUseCaches url-connection false)
    (.addRequestProperty url-connection "authorization" authorization-header)
    (.addRequestProperty url-connection "x-amz-date" x-amz-date)
    (.addRequestProperty url-connection "x-amz-content-sha256" hashed-payload)
    (when session-token
      (.addRequestProperty url-connection "x-amz-security-token" session-token))
    url-connection))

(defn do-sideload [bucket src ns-symbol]
  (println (format "Sideloader active for s3://%s/%s" bucket src))
  (let [t (Thread/currentThread)]
    (when-not (instance? clojure.lang.DynamicClassLoader (.getContextClassLoader t))
      (println "Installing DynamicClassLoader")
      (.setContextClassLoader t (clojure.lang.DynamicClassLoader. (.getContextClassLoader t)))))
  (let [url-spec (format "https://%s.s3.%s.amazonaws.com/%s"
                         bucket
                         (System/getenv "AWS_REGION")
                         src)
        timestamp (swap! src-last-modified
                         (fn [v]
                           (if (not v)
                             (do
                               (println "First load")
                               (let [url (URL. url-spec)]
                                 (with-open [in (.getInputStream (open-signed-connection url))]
                                   (io/copy in (io/file "/tmp/src.zip")))
                                 (.addURL (.getContextClassLoader (Thread/currentThread)) (URL. "file:/tmp/src.zip"))
                                 (let [url-connection (open-signed-connection url)
                                       status (.getResponseCode url-connection)
                                       last-modified (.getLastModified url-connection)]
                                   (when (not= 200 status)
                                     (println "Sideloader failed to new code load: " status))
                                   (.disconnect url-connection)
                                   last-modified)))
                             (do
                               (println "Successive load")
                               (let [url (URL. url-spec)
                                     url-connection (open-signed-connection url)
                                     status (.getResponseCode url-connection)
                                     last-modified (.getLastModified url-connection)]
                                 (.disconnect url-connection)
                                 (when (not= 200 status)
                                   (println "Sideloader failed to load new code load: " status))
                                 (when (not= last-modified v)
                                   (println "New source available, reloading")
                                   (with-open [in (.getInputStream (open-signed-connection url))]
                                     (io/copy in (io/file "/tmp/src.zip")))
                                   (.setDefaultUseCaches (.openConnection (URL. "file:/tmp/src.zip")) false)
                                   (require ns-symbol :reload-all)
                                   (println "reload done"))
                                 last-modified)))))]
    (println (str "Sideload src timestamp: " timestamp))))

(defn sideload [ns-symbol]
  (let [sideload-bucket (System/getenv "SIDELOAD_BUCKET")
        sideload-src (System/getenv "SIDELOAD_SRC")
        sideload-enabled (System/getenv "SIDELOAD_ENABLED")]
    (when (and sideload-bucket sideload-src sideload-enabled)
      (do-sideload sideload-bucket sideload-src ns-symbol))))
