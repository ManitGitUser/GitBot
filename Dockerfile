FROM ubuntu:latest
LABEL authors="flux_capacitor"

ENTRYPOINT ["top", "-b"]