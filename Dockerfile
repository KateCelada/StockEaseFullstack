# StockEase - production image (Render.com)
#
# PHP 8.5 (floating tag, auto-picks up security patches on rebuild) with the
# pdo_mysql extension to connect to TiDB Serverless over TLS (CA bundle:
# tidb-ca.pem at the repo root, selected via the DB_SSL_CA environment
# variable in stockease_api/db.php).
#
# Bumped 8.2 -> 8.5 for the 21 Oct 2026 critical security release
# (CVE-2026-109618 / CVE-2026-109619, unauthenticated RCE).
# NOTE: PHP 8.2 reaches EOL on 31 Dec 2026.
#
# Render provides $PORT; router.php serves the admin panel
# (/stockease_admin/) and the API (/stockease_api/) from the repo root.

FROM php:8.5-cli

# pdo_mysql builds against PHP's bundled mysqlnd - no extra system libs needed.
RUN docker-php-ext-install pdo_mysql

# Production-ish PHP settings. db.php sets the real timezone (Asia/Singapore)
# at runtime; the API returns JSON errors, so HTML error display is off.
RUN printf "display_errors=Off\nlog_errors=On\ndate.timezone=UTC\n" \
    > /usr/local/etc/php/conf.d/stockease.ini

WORKDIR /app
COPY . ./

# Shell form so $PORT expands from the environment.
CMD php -S 0.0.0.0:$PORT router.php
