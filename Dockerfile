FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/eclipse-temurin:17-jdk-alpine

MAINTAINER zhouxx@gmail.com

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms512m -Xmx2048m -XX:+StartAttachListener -Djava.security.egd=file:/dev/./urandom"

RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN mkdir -p /zeus-ops-workorder

WORKDIR /zeus-ops-workorder

EXPOSE 9000

ADD ./target/zeus-ops-workorder.jar ./

CMD java $JAVA_OPTS -jar zeus-ops-workorder.jar
