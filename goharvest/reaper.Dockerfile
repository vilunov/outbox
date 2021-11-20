FROM golang:1.17 as build

WORKDIR /app

RUN go install github.com/obsidiandynamics/goharvest/cmd/reaper@1239a594e9dcbb1945120fa2b86caf3021c101bb

ENTRYPOINT ["reaper"]
