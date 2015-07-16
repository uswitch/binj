(ns binj.reporting
  (:import [com.microsoft.bingads AuthorizationData PasswordAuthentication ServiceClient]
           [com.microsoft.bingads.reporting IReportingService KeywordPerformanceReportRequest KeywordPerformanceReportColumn ReportFormat ReportAggregation AccountThroughAdGroupReportScope AccountReportScope ReportTime ReportTimePeriod ArrayOfKeywordPerformanceReportColumn SubmitGenerateReportRequest ArrayOflong ApiFaultDetail_Exception PollGenerateReportRequest ReportRequestStatusType]
           [java.util.zip ZipInputStream]
           [java.io StringWriter])
  (:require [clj-http.lite.client :as http]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]))


(defn authorization-data [developer-token username password customer-id account-id]
  {:pre [(number? customer-id)
         (number? account-id)]}
  (doto (AuthorizationData. )
    (.setDeveloperToken developer-token)
    (.setAuthentication (PasswordAuthentication. username password))
    (.setCustomerId     customer-id)
    (.setAccountId      account-id)))

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
                                 :ekyword-status  KeywordPerformanceReportColumn/KEYWORD_STATUS
                                 :network         KeywordPerformanceReportColumn/NETWORK
                                 :device-type     KeywordPerformanceReportColumn/DEVICE_TYPE
                                 :device-os       KeywordPerformanceReportColumn/DEVICE_OS
                                 :bid-match-type  KeywordPerformanceReportColumn/BID_MATCH_TYPE})

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

(def report-status {ReportRequestStatusType/SUCCESS :success
                    ReportRequestStatusType/ERROR   :error
                    ReportRequestStatusType/PENDING :pending})


(defn read-entry [zip-stream entry]
  (let [header-rows 10
        writer      (StringWriter. (.getSize entry))]
    (io/copy zip-stream writer :encoding "UTF-8")
    (let [record-lines       (s/join \newline (drop header-rows (s/split (.toString writer) #"\r\n")))
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
      (catch ApiFaultDetail_Exception e
        (let [fault-info (.getFaultInfo e)]
          {:error {:operation-errors (map (fn [e] {:code    (.getCode e)
                                                  :details (.getDetails e)
                                                  :message (.getMessage e)})
                                          (.. fault-info getOperationErrors getOperationErrors))}})))))

(defn poll-report
  "Retrieves the report's download URL and its status (:success, :pending etc.)"
  [reporting-service request-id]
  (let [poll-request (doto (PollGenerateReportRequest. )
                       (.setReportRequestId request-id))
        response     (.pollGenerateReport (.getService reporting-service) poll-request)
        status       (.getReportRequestStatus response)]
    {:report-url     (.getReportDownloadUrl status)
     :status         (report-status (.getStatus status))}))

(defn record-seq
  "Downloads the report and returns an array-map for each report row."
  [report-url]
  (->> (entries-seq report-url)
       (remove :directory)
       (mapcat :records)))
