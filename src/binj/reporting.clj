(ns binj.reporting
  (:import [com.microsoft.bingads AuthorizationData PasswordAuthentication ServiceClient OAuthDesktopMobileAuthCodeGrant NewOAuthTokensReceivedListener OAuthWebAuthCodeGrant]
           [com.microsoft.bingads.internal OAuthWithAuthorizationCode LiveComOAuthService]
           [com.microsoft.bingads.reporting IReportingService KeywordPerformanceReportRequest KeywordPerformanceReportColumn ReportFormat ReportAggregation AccountThroughAdGroupReportScope AccountReportScope ReportTime ReportTimePeriod ArrayOfKeywordPerformanceReportColumn SubmitGenerateReportRequest ArrayOflong PollGenerateReportRequest ReportRequestStatusType AccountPerformanceReportColumn AccountPerformanceReportRequest AccountReportScope ArrayOfAccountPerformanceReportColumn AdApiFaultDetail_Exception]
           [java.util.zip ZipInputStream]
           [java.io StringWriter FileNotFoundException]
           [java.net URL])
  (:require [clj-http.lite.client :as http]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]))

(defn token-file-listener [file]
  (when file
    (proxy [NewOAuthTokensReceivedListener] []
      (onNewOAuthTokensReceived [tokens]
        (->> {:access-token (.getAccessToken tokens)
              :refresh-token (.getRefreshToken tokens)}
             (pr-str)
             (spit file))))))

(defn- read-token [file]
  (try
    (edn/read-string (slurp file))
    (catch FileNotFoundException e
      nil)))

(defn oauth-code-grant
  "Creates OAuth compatible code grant. When given just a client-id can
  be used to retrieve access and refresh tokens. If a refresh token has
  already been retrieved can use token-file instead- refresh tokens will
  automatically be saved here also."
  [client-id & {:keys [token-file]}]
  (let [tokens (read-token token-file)]
    (if-let [tokens (read-token token-file)]
      (let [refresh-token (:refresh-token tokens)]
        (doto (OAuthDesktopMobileAuthCodeGrant. client-id refresh-token)
          (.setNewTokensListener (token-file-listener token-file))))
      (let [grant (OAuthDesktopMobileAuthCodeGrant. client-id)]
        (when token-file
          (.setNewTokensListener grant (token-file-listener token-file)))
        grant))))

(defn authorization-url [grant]
  (.getAuthorizationEndpoint grant))

(defn- coerce-to-url [url]
  (if (instance? URL url)
    url
    (URL. url)))

(defn request-access-refresh-tokens [grant authorized-url]
  (try
    (let [url    (coerce-to-url authorized-url)
          tokens (.requestAccessAndRefreshTokens grant url)]
      {:access-token (.getAccessToken tokens)
       :refresh-token (.getRefreshToken tokens)})
    (catch Exception e
      (let [details (.getDetails e)]
        (throw (ex-info "Error requesting tokens"
                        {:error (.getError details)
                         :description (.getDescription details)}))))))

(defn authorization-data
  ([developer-token oauth-code-grant]
   (doto (AuthorizationData. )
     (.setDeveloperToken developer-token)
     (.setAuthentication oauth-code-grant)))
  ([developer-token username password customer-id account-id]
   (doto (AuthorizationData. )
     (.setDeveloperToken developer-token)
     (.setAuthentication (PasswordAuthentication. username password))
     (.setCustomerId     customer-id)
     (.setAccountId      account-id))))

(defn reporting-service [authorization-data]
  (ServiceClient. authorization-data IReportingService))

(def keyword-performance-column {:account-name    KeywordPerformanceReportColumn/ACCOUNT_NAME
                                 :account-number  KeywordPerformanceReportColumn/ACCOUNT_NUMBER
                                 :campaign-name   KeywordPerformanceReportColumn/CAMPAIGN_NAME
                                 :account-id      KeywordPerformanceReportColumn/ACCOUNT_ID
                                 :time-period     KeywordPerformanceReportColumn/TIME_PERIOD
                                 :keyword         KeywordPerformanceReportColumn/KEYWORD
                                 :impressions     KeywordPerformanceReportColumn/IMPRESSIONS
                                 :clicks          KeywordPerformanceReportColumn/CLICKS
                                 :spend           KeywordPerformanceReportColumn/SPEND
                                 :average-cpc     KeywordPerformanceReportColumn/AVERAGE_CPC
                                 :quality-score   KeywordPerformanceReportColumn/QUALITY_SCORE
                                 :account-status  KeywordPerformanceReportColumn/ACCOUNT_STATUS
                                 :campaign-status KeywordPerformanceReportColumn/CAMPAIGN_STATUS
                                 :keyword-status  KeywordPerformanceReportColumn/KEYWORD_STATUS
                                 :network         KeywordPerformanceReportColumn/NETWORK
                                 :device-type     KeywordPerformanceReportColumn/DEVICE_TYPE
                                 :device-os       KeywordPerformanceReportColumn/DEVICE_OS
                                 :bid-match-type  KeywordPerformanceReportColumn/BID_MATCH_TYPE})


(def account-performance-column {:account-name    AccountPerformanceReportColumn/ACCOUNT_NAME
                                 :account-number  AccountPerformanceReportColumn/ACCOUNT_NUMBER
                                 :account-id      AccountPerformanceReportColumn/ACCOUNT_ID
                                 :time-period     AccountPerformanceReportColumn/TIME_PERIOD
                                 :impressions     AccountPerformanceReportColumn/IMPRESSIONS
                                 :clicks          AccountPerformanceReportColumn/CLICKS
                                 :spend           AccountPerformanceReportColumn/SPEND
                                 :average-cpc     AccountPerformanceReportColumn/AVERAGE_CPC
                                 :network         AccountPerformanceReportColumn/NETWORK
                                 :device-type     AccountPerformanceReportColumn/DEVICE_TYPE
                                 :device-os       AccountPerformanceReportColumn/DEVICE_OS})

(def report-aggregation {:summary     ReportAggregation/SUMMARY
                         :hourly      ReportAggregation/HOURLY
                         :daily       ReportAggregation/DAILY
                         :weekly      ReportAggregation/WEEKLY
                         :monthly     ReportAggregation/MONTHLY
                         :yearly      ReportAggregation/YEARLY
                         :hour-of-day ReportAggregation/HOUR_OF_DAY
                         :day-of-week ReportAggregation/DAY_OF_WEEK})

(def report-scope {:account (AccountReportScope. )})

(def report-time-period {:yesterday   ReportTimePeriod/YESTERDAY
                         :today       ReportTimePeriod/TODAY
                         :last-7-days ReportTimePeriod/LAST_SEVEN_DAYS
                         :last-month  ReportTimePeriod/LAST_MONTH
                         :this-year   ReportTimePeriod/THIS_YEAR
                         :last-year   ReportTimePeriod/LAST_YEAR})

(defn report-time [period]
  (doto (ReportTime. )
    (.setPredefinedTime (report-time-period period))))

(defn keyword-performance-report-columns [cols]
  (let [cols          (map (partial get keyword-performance-column) cols)
        array-of-cols (ArrayOfKeywordPerformanceReportColumn. )]
    (doseq [c cols]
      (.add (.getKeywordPerformanceReportColumns array-of-cols) c))
    array-of-cols))


(defn keyword-performance-report-request
  [name account-ids columns & {:keys [complete-only? aggregation time-period]
                               :or   {complete-only? true
                                      aggregation    :daily
                                      time-period    :yesterday}}]
  (let [account-longs (ArrayOflong.)]
    (doseq [id account-ids]
      (.add (.getLongs account-longs) id))
    (doto (KeywordPerformanceReportRequest. )
      (.setFormat                 ReportFormat/TSV)
      (.setReportName             name)
      (.setReturnOnlyCompleteData complete-only?)
      (.setAggregation            (report-aggregation aggregation))
      (.setScope                  (doto (AccountThroughAdGroupReportScope. )
                                    (.setAccountIds account-longs)))
      (.setTime                   (report-time time-period))
      (.setColumns                (keyword-performance-report-columns columns)))))


(defn account-performance-report-columns [cols]
  (let [cols          (map (partial get account-performance-column) cols)
        array-of-cols (ArrayOfAccountPerformanceReportColumn. )]
    (doseq [c cols]
      (.add (.getAccountPerformanceReportColumns array-of-cols) c))
    array-of-cols))

(defn account-performance-report-request
  [name account-ids columns & {:keys [complete-only? aggregation time-period]
                               :or   {complete-only? true
                                      aggregation    :daily
                                      time-period    :yesterday}}]

  (let [account-longs (ArrayOflong.)]
    (doseq [id account-ids]
      (.add (.getLongs account-longs) id))
    (doto (AccountPerformanceReportRequest. )
      (.setFormat                 ReportFormat/TSV)
      (.setReportName             name)
      (.setReturnOnlyCompleteData complete-only?)
      (.setAggregation            (report-aggregation aggregation))
      (.setScope                  (doto (AccountReportScope. )
                                    (.setAccountIds account-longs)))
      (.setTime                   (report-time time-period))
      (.setColumns                (account-performance-report-columns columns)))))

(def report-status {ReportRequestStatusType/SUCCESS :success
                    ReportRequestStatusType/ERROR   :error
                    ReportRequestStatusType/PENDING :pending})

(defn lines [string-writer]
  (s/split (.toString string-writer) #"\r\n"))

(defn read-entry [zip-stream entry]
  (let [header-rows 10
        footer-rows 2
        writer      (StringWriter. (.getSize entry))]
    (io/copy zip-stream writer :encoding "UTF-8")
    (let [record-lines       (->> (lines writer)
                                  (drop header-rows)
                                  (drop-last footer-rows)
                                  (s/join \newline))
          [header & records] (csv/read-csv record-lines :separator \tab)]
      {:name      (.getName entry)
       :directory (.isDirectory entry)
       :size      (.getSize entry)
       :header    header
       :records   (map (fn [record] (apply array-map (interleave header record))) records)})))

(defn entries-seq [report-url]
  (let [{:keys [body]} (http/get report-url {:as :stream})
        zip-stream (ZipInputStream. body)]
    (loop [entry   (.getNextEntry zip-stream)
           entries (list)]
      (if (nil? entry)
        entries
        (let [added (conj entries (read-entry zip-stream entry))]
          (recur (.getNextEntry zip-stream) added))))))



(defn generate-report
  "Submits the report request to the API. Returns a map with an :error
  if the request can't be processed. If successful returns :request-id
  that can be used with poll-report."
  [reporting-service report-request]
  (let [generate-request (doto (SubmitGenerateReportRequest.)
                           (.setReportRequest report-request))]
    (try
      {:request-id (.getReportRequestId (.submitGenerateReport (.getService reporting-service) generate-request))}
      (catch AdApiFaultDetail_Exception e
        (let [fault-info (.getFaultInfo e)]
          {:error {:operation-errors (map (fn [e] {:code    (.getCode e)
                                                  :details (.getDetail e)
                                                  :message (.getMessage e)})
                                          (.. fault-info getErrors getAdApiErrors))}})))))

(defn poll-report
  "Retrieves the report's download URL and its status (:success, :pending etc.)"
  [reporting-service request-id]
  (let [poll-request (doto (PollGenerateReportRequest. )
                       (.setReportRequestId request-id))
        response     (.pollGenerateReport (.getService reporting-service) poll-request)
        status       (.getReportRequestStatus response)]
    {:report-url     (.getReportDownloadUrl status)
     :status         (report-status (.getStatus status))}))

(def gregorian-date-format (tf/formatter "MM/dd/yyyy"))

(defn parse-date [s] (tc/to-local-date (tf/parse gregorian-date-format s)))
(defn parse-long [s] (Long/valueOf s))
(defn parse-double [s] (Double/parseDouble s))

(def parsers {"GregorianDate" parse-date
              "Impressions"   parse-long
              "Clicks"        parse-long
              "Spend"         parse-double
              "AverageCpc"    parse-double})

(defn parser [k]
  (or (parsers k) identity))

(defn coerce-record [m]
  (into (array-map)
        (for [[k v] m]
          (let [f (parser k)]
            [k (f v)]))))

(defn record-seq
  "Downloads the report and returns an array-map for each report row."
  [report-url]
  (->> (entries-seq report-url)
       (remove :directory)
       (mapcat :records)
       (map coerce-record)))
