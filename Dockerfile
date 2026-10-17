# StockEase - production image (Render.com)
#
# PHP 8.2 with the pdo_mysql extension to connect to TiDB Serverless
# over TLS (CA bundle: tidb-ca.pem at the repo root, selected via the
# DB_SSL_CA environment variable in stockease_api/db.php).
#
# Render provides $PORT; router.php serves the admin panel
# (/stockease_admin/) and the API (/stockease_api/) from the repo root.

FROM php:8.2-cli

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
