# syntax=docker/dockerfile:1
#
# Generic multi-stage build for any service module in this monorepo
# (guide §20: every service ships a multi-stage, non-root Dockerfile with
# a pinned base image — one pattern, reused via build args rather than
# duplicated per service).
#
# Build:
#   docker build --build-arg MODULE=gateway --build-arg JAR_NAME=gateway-1.0.0-SNAPSHOT.jar -t gateway .
#   docker build --build-arg MODULE=services/customer-service --build-arg JAR_NAME=customer-service-1.0.0-SNAPSHOT.jar -t customer-service .
#   docker build --build-arg MODULE=services/kyc-service --build-arg JAR_NAME=kyc-service-1.0.0-SNAPSHOT.jar -t kyc-service .
#   docker build --build-arg MODULE=services/aml-service --build-arg JAR_NAME=aml-service-1.0.0-SNAPSHOT.jar -t aml-service .
#   docker build --build-arg MODULE=services/document-service --build-arg JAR_NAME=document-service-1.0.0-SNAPSHOT.jar -t document-service .
#   docker build --build-arg MODULE=platform/audit-service --build-arg JAR_NAME=audit-service-1.0.0-SNAPSHOT.jar -t audit-service .
#   docker build --build-arg MODULE=integration/crm-adapter --build-arg JAR_NAME=crm-adapter-1.0.0-SNAPSHOT.jar -t crm-adapter .
#   docker build --build-arg MODULE=integration/onboarding-adapter --build-arg JAR_NAME=onboarding-adapter-1.0.0-SNAPSHOT.jar -t onboarding-adapter .
#   docker build --build-arg MODULE=integration/fund-mgmt-adapter --build-arg JAR_NAME=fund-mgmt-adapter-1.0.0-SNAPSHOT.jar -t fund-mgmt-adapter .

ARG JDK_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-jammy

FROM ${JDK_IMAGE} AS build
ARG MODULE
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -pl ${MODULE} -am -DskipTests package

FROM ${JRE_IMAGE}
ARG MODULE
ARG JAR_NAME

RUN groupadd --system --gid 1000 appuser \
    && useradd --system --uid 1000 --gid appuser --no-create-home appuser

WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${JAR_NAME} app.jar
# The whole directory, not just the jar: common-logging's RollingFileAppender
# needs to create logs/ under the working directory at runtime, and the
# non-root user below can't do that unless it owns the directory itself.
RUN mkdir -p /app/logs && chown -R appuser:appuser /app

USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
