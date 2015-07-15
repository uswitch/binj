(ns binj.reporting
  (:import [com.microsoft.bingads AuthorizationData PasswordAuthentication ServiceClient]
           [com.microsoft.bingads.reporting IReportingService KeywordPerformanceReportRequest KeywordPerformanceReportColumn ReportFormat ReportAggregation AccountThroughAdGroupReportScope ReportTime ReportTimePeriod ArrayOfKeywordPerformanceReportColumn SubmitGenerateReportRequest]))


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

(def report-format {:tsv ReportFormat/TSV
                    :csv ReportFormat/CSV
                    :xml ReportFormat/XML})

(def report-aggregation {:summary     ReportAggregation/SUMMARY
                         :hourly      ReportAggregation/HOURLY
                         :daily       ReportAggregation/DAILY
                         :weekly      ReportAggregation/WEEKLY
                         :monthly     ReportAggregation/MONTHLY
                         :yearly      ReportAggregation/YEARLY
                         :hour-of-day ReportAggregation/HOUR_OF_DAY
                         :day-of-week ReportAggregation/DAY_OF_WEEK})

;;(def report-scope {:account-through-adgroup (AccountThroughAdGroupReportScope. )})

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
  [name columns & {:keys [format complete-only? aggregation scope time-period]
                   :or   {format         :tsv
                          complete-only? true
                          aggregation    :daily
                          scope          :account-through-campaign
                          time-period    :yes}}]
  (doto (KeywordPerformanceReportRequest. )
    (.setFormat                 (report-format format))
    (.setReportName             name)
    (.setReturnOnlyCompleteData complete-only?)
    (.setAggregation            (report-aggregation aggregation))
    (.setTime                   (report-time time-period))
    (.setColumns                (keyword-performance-report-columns columns))))

(defn generate-report [reporting-service report-request]
  (let [generate-request (doto (SubmitGenerateReportRequest.)
                           (.setReportRequest report-request))]
    {:request-id (.getReportRequestId (.submitGenerateReport (.getService reporting-service) generate-request))}))
