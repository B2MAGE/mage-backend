# Local development: pinned AGPL community sources; no AIStor activation key.
FROM golang:1.24.9-bookworm AS server-build
ENV CGO_ENABLED=0 GOTOOLCHAIN=local
RUN go install -trimpath github.com/minio/minio@9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a

FROM golang:1.24.9-bookworm AS client-build
ENV CGO_ENABLED=0 GOTOOLCHAIN=local
RUN go install -trimpath github.com/minio/mc@7394ce0dd2a80935aded936b09fa12cbb3cb8096

FROM alpine:3.22
RUN apk add --no-cache ca-certificates curl
COPY --from=server-build /go/bin/minio /usr/local/bin/minio
COPY --from=client-build /go/bin/mc /usr/local/bin/mc
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
