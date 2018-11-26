# binj

Clojure library to access the Bing Ads reporting API.

[![Clojars Project](https://img.shields.io/clojars/v/uswitch/binj.svg)](https://clojars.org/uswitch/binj)

## Example Usage

For this example to work you need:

* a developer token
* client-id
* access tokens in an edn file

Notes on how these are generated are below.

```clojure
(ns example
  (:require [binj.reporting :as b]
            [clj-time.core  :as t]))

(def account-id 12345)
(def dev-token "123456789MAGIC42")
(def client-id "deadbeef-1234-5678-9009-424242424242")
(def grant (b/oauth-code-grant client-id :token-file "bing.edn"))
(def auth (b/authorization-data developer-token grant))

(def report-request (b/account-performance-report-request
    "name"
    [account-id]
    [:time-period :account-id :account-name :account-number :spend]
    :aggregation :daily
    :time-period {:start (t/date-time 2018 3 6) :end (t/date-time 2018 3 6)}))

(def results (b/submit-and-download report-request auth))

({"TimePeriod" "2018-03-06", "AccountId" "12345", "AccountName" "uSwitch Some Account", "AccountNumber" "X9876543", "Spend" 1666.66})
```

## Getting a developer-token

[Bing Developer Token docs](https://docs.microsoft.com/en-us/bingads/guides/get-started?view=bingads-12#get-developer-token)

1. Make sure the user you will use to run the app has been at least granted "Client Viewer" permissions
2. Have a super user generate and assign that user a "Developer Token" by clicking the "request token" button in the [developer portal](https://developers.bingads.microsoft.com/Account)
3. The token should now be visible to the user on the [accounts page](https://developers.bingads.microsoft.com/Account)

## Getting a client-id

[Bing OAuth Set up docs](https://docs.microsoft.com/en-us/bingads/guides/authentication-oauth?view=bingads-12#registerapplication)

1.  Visit the [microsoft app portal](https://apps.dev.microsoft.com)
2.  "Add an app" and name it
3.  In the app's config page click "Add platform" and select "native"
4.  Save the changes
5.  Copy the "Application Id". This is the client-id

## Generate Tokens

[Bing OAuth SDK docs](https://docs.microsoft.com/en-us/bingads/guides/sdk-authentication?view=bingads-12#oauth)

If you're using a new client-id or developer token you'll need to generate a token file. This is best done from a repl:

```clojure
$ lein repl
...
user=> (require '[binj.reporting :as b])
user=> (def client-id "deadbeef-1234-5678-9009-424242424242")
user=> (def grant (b/oauth-code-grant client-id))
user=> (b/authorization-url grant)
#object[java.net.URL 0xdeadbeef "https://login.live.com/oauth20_authorize.srf?scope=bingads.manage&response_type=code&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf&client_id=deadbeef-1234-5678-9009-424242424242"]
```

Visit the link generated and sign in to your BingAds account then grant your app the requested permissions. Copy the URL of the page you're redirected to

```clojure
; Using the same session as before
user=> (def resp-url "https://login.live.com/oauth20_desktop.srf?code=SomeOther-UUID-THAT-OAUT-HNeedsToWork&lc=1666")
user=> (b/request-access-refresh-tokens grant resp-url)
{:access-token "some huge string", :refresh-token "some other string"}
```

The output of the final command should be saved so it can be used as the token-file.

## Debugging

[Bing Docs](https://docs.microsoft.com/en-us/bingads/guides/handle-service-errors-exceptions?view=bingads-12)

If you see errors like:
```bash
AdApiFaultDetail_Exception Invalid client data. Check the SOAP fault details for more information  sun.reflect.NativeConstructorAccessorImpl.newInstance0 (NativeConstructorAccessorImpl.java:-2)
```
Then the API is swallowing SOAP errors. More verbose output can be gained by running:
```clojure
(. System setProperty "com.sun.xml.ws.transport.http.client.HttpTransportPipe.dump" "true")
```

## Changelog

### Version 0.5.0

- Removed support for username/password authentication (depreciated by Bing)

### Version 0.4.0

- Support for v11 BingAds api
- breaking changes to report download
- Async polling for report download internalised  (`request-id` no longer exposed)
- `submit-and-download` now returns a sequence of maps
- requires permission to generate a temporary file on the file system

**Note:** With version 0.4.0 there are some significant breaking changes to the the way reports are generated.  It now uses the built in async polling mechanism provided in the SDK, so it no longer exposes the request-id.  The `submit-and-download` now returns a sequence of maps representing the report data.

Authorisation and Report Definitions are the same, however the mechanism to download them is breaking.  Please be aware when upgrading to this version.

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
