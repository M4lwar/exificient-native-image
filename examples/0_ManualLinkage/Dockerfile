FROM ghcr.io/graalvm/native-image-community:21 AS build
RUN microdnf install -y maven && microdnf clean all
WORKDIR /build

COPY pom.xml .
RUN mvn -B -Pnative dependency:go-offline

COPY src/ src/
COPY schemas/ schemas/

RUN mvn -B -Pnative test
RUN mvn -B -Pnative package -DskipTests

FROM ubuntu:22.04
RUN apt-get update && apt-get install -y --no-install-recommends g++ && rm -rf /var/lib/apt/lists/*

WORKDIR /test

COPY --from=build /build/target/*.so ./
COPY --from=build /build/target/*.h ./
COPY examples/0_ManualLinkage/test.cpp ./
COPY src/test/resources/PositionReport.xml ./
COPY schemas/ schemas/

RUN g++ -o test_exi test.cpp -L. -lexificient -I.

CMD ["sh", "-c", "LD_LIBRARY_PATH=. ./test_exi PositionReport.xml"]
