FROM php:8.3-fpm-alpine

RUN apk add --no-cache curl nginx supervisor \
    && apk add --no-cache --virtual .build-deps $PHPIZE_DEPS oniguruma-dev \
    && docker-php-ext-install pdo_mysql mbstring \
    && apk del .build-deps \
    && mkdir -p /run/nginx /var/lib/nginx/tmp /src/public

WORKDIR /src
COPY public/ /src/public/
COPY nginx.conf /etc/nginx/nginx.conf
COPY php.ini /usr/local/etc/php/conf.d/99-starloco.ini
COPY supervisord.conf /etc/supervisord.conf

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=8s --start-period=20s --retries=5 \
  CMD curl -fsS http://127.0.0.1/health.php?deep=1 || exit 1

CMD ["supervisord", "-c", "/etc/supervisord.conf"]
