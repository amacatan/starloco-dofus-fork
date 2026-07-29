FROM starloco-offline/login:prepared
ARG BUILD_DATE=unknown
ARG SOURCE_REVISION=unknown
ARG SOURCE_DIRTY=unknown
LABEL org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.revision="$SOURCE_REVISION" \
      dev.starloco.source-dirty="$SOURCE_DIRTY"
USER root
RUN rm -f /app/login.jar
COPY --chown=1000:1000 login.jar /app/login.jar
COPY --chown=1000:1000 entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh
USER starloco
