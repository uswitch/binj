(ns binj.reporting
  (:import [com.microsoft.bingads ServiceClient AuthorizationData PasswordAuthentication OAuthDesktopMobileAuthCodeGrant NewOAuthTokensReceivedListener]
           [com.microsoft.bingads PasswordAuthentication]
           [com.microsoft.bingads.v11.reporting ReportingServiceManager Date IReportingService KeywordPerformanceReportRequest KeywordPerformanceReportColumn ReportFormat ReportAggregation AccountThroughAdGroupReportScope AccountReportScope ReportTime ReportTimePeriod ArrayOfKeywordPerformanceReportColumn SubmitGenerateReportRequest ArrayOflong PollGenerateReportRequest ReportRequestStatusType AccountPerformanceReportColumn AccountPerformanceReportRequest ArrayOfAccountPerformanceReportColumn]
           [java.util.concurrent TimeUnit]
           [java.io StringWriter FileNotFoundException]
           [java.net URL])
  (:import org.apache.commons.io.input.BOMInputStream)
  (:require [clj-http.lite.client :as http]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.edn :as edn]
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
  (when file
    (try
      (edn/read-string (slurp file))
      (catch FileNotFoundException e
        nil))))

(defn password-grant
  [username password]
  (PasswordAuthentication. username password))

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
  [developer-token authentication & {:keys [customer-id account-id]}]
  (let [authorize (AuthorizationData. )]
    (.setDeveloperToken authorize developer-token)
    (.setAuthentication authorize authentication)
    (when customer-id
      (.setCustomerId authorize customer-id))
    (when account-id
      (.setAccountId authorize account-id))
    authorize))

(def keyword-performance-column {:account-name    KeywordPerformanceReportColumn/ACCOUNT_NAME
                                 :account-number  KeywordPerformanceReportColumn/ACCOUNT_NUMBER
                                 :ad-group-name   KeywordPerformanceReportColumn/AD_GROUP_NAME
                                 :ad-group-id     KeywordPerformanceReportColumn/AD_GROUP_ID
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

(defprotocol ToBingDate
  (to-bing-date [x]))

(extend-protocol ToBingDate
  org.joda.time.DateTime
  (to-bing-date [x] (doto (Date.)
                      (.setYear (.getYear x))
                      (.setMonth (.getMonthOfYear x))
                      (.setDay (.getDayOfMonth x)))))

(defn report-time
  "Defines a reporting period. Can either be a pre-defined interval, or
  a custom date range with start and end dates. Dates must extend
  ToBingDate protocol."
  [period]
  (cond (keyword? period) (doto (ReportTime. )
                            (.setPredefinedTime (report-time-period period)))
        (map? period)     (let [{:keys [start end]} period]
                            (doto (ReportTime. )
                              (.setCustomDateRangeStart (to-bing-date start))
                              (.setCustomDateRangeEnd (to-bing-date end))))
        :else             (throw (ex-info "invalid report-time period." {:period period}))))

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
      (.setExcludeReportHeader    true)
      (.setExcludeReportFooter    true)
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
      (.setColumns                (account-performance-report-columns columns))
      (.setExcludeReportHeader    true)
      (.setExcludeReportFooter    true))))

(defn parse-date [s] (tc/from-string s))
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

(defn csv-data->maps  [[headers & data]]
  (map zipmap
       (repeat headers)
       data))

(defn report->maps [file]
  (-> file
    io/input-stream
    BOMInputStream.
    io/reader
    (csv/read-csv :separator \tab)
    csv-data->maps))

(defn submit-and-download [report-request auth]
  (let [reporting-service-manager    (doto (ReportingServiceManager. auth) (.setStatusPollIntervalInMilliseconds 5000))
        reporting-download-operation (.. reporting-service-manager (submitDownloadAsync report-request nil) get)
        filename                     (str (java.util.UUID/randomUUID) ".tsv")
        _                            (.. reporting-download-operation (trackAsync nil) (get 60000, TimeUnit/MILLISECONDS))
        report-file                  (.. reporting-download-operation (downloadResultFileAsync (clojure.java.io/file "/tmp") filename true true nil) get)
        result (->> report-file report->maps (map coerce-record)) ]
    (.delete (io/file (str "/tmp/" filename)))
    result))

