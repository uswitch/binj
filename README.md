# binj

Clojure library to access the Bing Ads reporting API.

[![Clojars Project](https://img.shields.io/clojars/v/uswitch/binj.svg)](https://clojars.org/uswitch/binj)

## Note Breaking Changes 0.4.0

With version 0.4.0 there are some significant breaking changes to the the way reports are generated.  It now uses the built in
async polling mechanism provided in the SDK, so it no longer exposes the request-id.  The `submit-and-download` now returns a sequence of maps
representing the report data.

Authorisation and Report Definitions are the same, however the mechanism to download them is breaking.  Please be aware when upgrading to this version.

## Example Usage

```clojure
(ns example
  (:require [binj.reporting :as b]
            [clj-time.core  :as t]))

(def auth (b/authorization-data developer-token (b/password-grant username password)))

(def report-request (b/account-performance-report-request
    "name"
    [account-id]
    [:time-period :account-id :account-name :account-number :spend]
    :aggregation :daily
    :time-period {:start (t/date-time 2018 3 6) :end (t/date-time 2018 3 6)}))

(def results (b/submit-and-download report-request auth))

({"GregorianDate" #object[org.joda.time.DateTime 0x76248127 "2018-03-06T00:00:00.000Z"], "AccountId" "12345", "AccountName" "My Bing Account", "AccountNumber" "X1234567", "Spend" 1234.00})

```

## Changelog

### Version 0.4.0

- Support for v11 BingAds api
- breaking changes to report download
- Polling internalised
- requires permission to generate a temporary file on the file system

### Version 0.3,0-SNAPSHOT

- Support for v11 BingAds api
- Broken

### Version 0.2,0-SNAPSHOT

- Support for v10 BingAds api


### Version 0.1,2

- Support for v9 BingAds api

## License

Copyright © 2018, uSwitch.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
