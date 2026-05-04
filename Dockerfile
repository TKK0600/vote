FROM ubuntu:latest
LABEL authors="kahki"

ENTRYPOINT ["top", "-b"]