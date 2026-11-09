
package com.example.autoiprotator

/**
 * Official Riseup / LEAP CA certificate.
 * Source: https://black.riseup.net/ca.crt
 *
 * This certificate is used to validate Riseup API TLS.
 * Do not disable hostname verification or trust arbitrary certificates.
 */
internal object RiseupCa {

    const val PEM = """
-----BEGIN CERTIFICATE-----
MIIBYjCCAQigAwIBAgIBATAKBggqhkjOPQQDAjAXMRUwEwYDVQQDEwxMRUFQIFJv
b3QgQ0EwHhcNMjYxMTAzMTkwNDQ4WhcNMzExMTAzMTkwOTQ4WjAXMRUwEwYDVQQD
EwxMRUFQIFJvb3QgQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQxOXBGu+gf
pjHzVteGTWL6XnFxtEnKMFpKaJkA/VOHmESzoLsZRQxt88GssxaqC01J17idQiqv
zgNpedmtvFtyo0UwQzAOBgNVHQ8BAf8EBAMCAqQwEgYDVR0TAQH/BAgwBgEB/wIB
ATAdBgNVHQ4EFgQUZdoUlJrCIUNFrpffAq+LQjnwEz4wCgYIKoZIzj0EAwIDSAAw
RQIgAQj003fHNaIWVth97ldbW7RFHIlPBMOpzlnIAXGRGqsCIQDM0Jup2mwcolhm
4tgylCf58Z5pmt7bXAn6fBtoFbwjmA==
-----END CERTIFICATE-----
"""
}
