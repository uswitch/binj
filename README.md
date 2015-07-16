# binj

Clojure library to access the Bing Ads reporting API.

## Example Usage

```clojure
(ns example
  (:require [binj.reporting :as b]))

(def auth (b/authorization-data developer-token username password customer-id account-id))
(def service (b/reporting-service auth))

(def report-request (b/keyword-performance-report-request "name"
                                                          [account-id]
                                                          [:time-period :account-name :campaign-name :keyword :impressions :clicks]))

(let [{:keys [request-id error] :as status} (b/generate-report service report-request)]
  (when error
    (println "Error submitting report:" error)
    (System/exit 1))

  (loop [{:keys [report-url status]} (b/poll-report service request-id)]
    (condp = status
      :success (let [records (b/record-seq report-url)]
                 (doseq [r (take 10 records)]
                   (println r)))
      :error   (do (println "Error generating report.") (System/exit 1))
      :pending (do (Thread/sleep 5000) (recur (b/poll-report service request-id))))))
```

## License

Copyright © 2015, uSwitch.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
