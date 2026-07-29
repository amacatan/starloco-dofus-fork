FROM starloco-offline/game:prepared
ARG BUILD_DATE=unknown
ARG SOURCE_REVISION=unknown
ARG SOURCE_DIRTY=unknown
LABEL org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.revision="$SOURCE_REVISION" \
      dev.starloco.source-dirty="$SOURCE_DIRTY"
USER root
RUN rm -rf /app/scripts/* /app/game.jar
COPY --chown=1000:1000 game.jar /app/game.jar
COPY --chown=1000:1000 scripts/ /app/scripts/
COPY --chown=1000:1000 entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh
USER starloco
